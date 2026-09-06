# Ecommerce Backend Implementation Plan & Analysis

This document synthesizes the requirements from `README.md` and provides a complete architectural design and implementation plan.

---

## Requirements Analysis & Invariants

Based on `README.md`, our system must guarantee the following critical invariants:

1. **Inventory Invariant**: Available inventory must never fall below zero (`available_inventory >= 0`), even under high concurrency.
2. **Cart Invariant**: A cart can only be checked out once (`status == OPEN -> CHECKED_OUT`). Modifying a checked-out cart is forbidden.
3. **Idempotency Invariant**: Retried checkout requests with the same `Idempotency-Key` must return the exact existing order result without double-charging inventory or applying discounts twice.
4. **Coupon Invariant**: 
   - A coupon can only be redeemed once.
   - A coupon is never marked as redeemed if the checkout transaction fails/rolls back.
   - A coupon milestone (every $n^{\text{th}}$ order) generates at most one coupon.
   - Discount amount must be deterministic and can never make the order total negative (`net_total >= 0`).
5. **Money Accuracy Invariant**: All monetary values must use `BigDecimal` with 2 decimal places (`HALF_UP` rounding mode) to prevent floating-point inaccuracies.
6. **Reporting Reconciliation**: Admin reporting must be read-only and dynamically reconcile with existing `Order`, `OrderItem`, and `Coupon` records.

---

## Detailed Component Design

### 1. Data Model (JPA Entities)

- **`Product`**: `id` (Long), `name` (String), `price` (BigDecimal), `availableInventory` (Integer), `version` (Long for optimistic lock fallback, though pessimistic lock will be used during checkout).
- **`Cart`**: `id` (Long), `status` (Enum: `OPEN`, `CHECKED_OUT`), `createdAt` (LocalDateTime).
- **`CartItem`**: `id` (Long), `cartId` (Long), `productId` (Long), `quantity` (Integer).
- **`Order`**: `id` (Long), `cartId` (Long), `grossTotal` (BigDecimal), `discountAmount` (BigDecimal), `netTotal` (BigDecimal), `couponCode` (String), `status` (Enum: `SUCCESS`), `createdAt` (LocalDateTime).
- **`OrderItem`**: `id` (Long), `orderId` (Long), `productId` (Long), `productName` (String), `unitPrice` (BigDecimal), `quantity` (Integer), `lineTotal` (BigDecimal).
- **`Coupon`**: `id` (Long), `code` (String), `discountPercentage` (BigDecimal), `isRedeemed` (Boolean), `milestoneOrderNumber` (Long), `createdAt` (LocalDateTime).
- **`IdempotencyRecord`**: `key` (String, PK), `orderId` (Long), `responseBody` (String), `createdAt` (LocalDateTime).

---

### 2. Concurrency & Transaction Strategy

- **Inventory Deduct Locking**: When checking out, product records are retrieved using pessimistic write locks (`@Lock(LockModeType.PESSIMISTIC_WRITE)`). This forces concurrent transactions attempting to check out the same product to queue up, eliminating race conditions on inventory deduction.
- **Coupon Redemption Locking**: During checkout with a coupon, the coupon row is locked via `@Lock(LockModeType.PESSIMISTIC_WRITE)`. If `isRedeemed` is `true`, transaction aborts.
- **Atomic Transactions**: The `@Transactional` boundary covers the entire checkout process (inventory validation, coupon redemption, order creation, idempotency key registration). Any failure (e.g. out of stock) rolls back the whole transaction, ensuring coupons are not lost or consumed on failure.
- **Idempotency Locking**: A database unique constraint on `IdempotencyRecord.key` guarantees that parallel requests with the same idempotency key are safely handled (one succeeds, the other catches a duplicate key constraint and fetches the created order).

---

### 3. API Contract Specifications

#### **Cart Operations**
- `POST /api/carts`: Creates a new cart. Returns `201 Created`.
- `GET /api/carts/{id}`: Returns cart details with live prices, individual item line totals, and overall total.
- `POST /api/carts/{id}/items`: Adds item `{ productId, quantity }`. Validates product existence and quantity > 0.
- `PUT /api/carts/{id}/items/{productId}`: Updates quantity `{ quantity }`. If quantity is 0, removes item.
- `DELETE /api/carts/{id}/items/{productId}`: Removes item from cart.

#### **Checkout Operations**
- `POST /api/checkout`:
  - **Headers**: `X-Idempotency-Key` (String, required for idempotency).
  - **Body**: `{ "cartId": 1, "couponCode": "DISC10" }`
  - **Returns**: `200 OK` with order details, or `400/409/422` with structured error details (`ErrorCode`, `message`).

#### **Order Operations**
- `GET /api/orders/{id}`: Retrieves order details by ID.

#### **Admin Operations**
- `POST /api/admin/coupons/generate`: Checks if an unrewarded milestone ($n^{\text{th}}$ order) is reached. If eligible, generates a coupon. Returns `201 Created` with coupon or `400 Bad Request` if no milestone eligible.
- `GET /api/admin/reports/summary`: Returns current analytics summary:
  - `purchasedQuantityByProduct`: Map of product name to total units sold.
  - `grossRevenue`, `totalDiscountsGranted`, `netRevenue`.
  - `couponsGenerated`, `couponsAvailable`, `couponsRedeemed`.
  - `totalPlacedOrders`.

---

### 4. Configuration & Seed Data

- Application configuration properties:
  - `app.coupon.milestone-n=5` (Every 5th order triggers a coupon milestone)
  - `app.coupon.discount-percentage-x=10.0` (10% discount)
- Startup Data Seeder (`DataInitializer`):
  - Seeds 5 products (e.g., Laptop [Qty: 10], Smartphone [Qty: 2], Headphones [Qty: 50], Keyboard [Qty: 1], Mouse [Qty: 100]).

---

### 5. `DECISIONS.md` Documentation Structure

We will populate `DECISIONS.md` covering:
1. **System Invariants**: Detailed list of non-negotiable correctness rules.
2. **Ambiguities & Trade-offs**: Price change strategy, payment abstraction choice, milestone index handling.
3. **Five Material Decisions**:
   - Decision 1: Pessimistic Locking vs Optimistic Locking for Inventory.
   - Decision 2: Idempotency Key Storage & Strategy.
   - Decision 3: Dynamic Cart Pricing vs Snapshot Cart Pricing.
   - Decision 4: Money Handling (`BigDecimal` scale and rounding).
   - Decision 5: Coupon Milestone Generation Semantics (Manual Admin Trigger vs Auto-generation).
4. **Transaction & Concurrency Strategy**.
5. **AI Usage & Corrections**.
6. **Future Scalability** (Distributed locks with Redis, DB partitioning, Async processing).
7. **First 2 Hours Next Steps**.

---

## Verification & Testing Strategy

1. **Unit Tests**:
   - `CartServiceTest`: Validation of item additions, quantity modifications, invalid inputs.
   - `CouponServiceTest`: Milestone logic, discount calculations, edge cases (discount > subtotal).

2. **Integration & Concurrency Tests**:
   - `CheckoutConcurrencyTest`: Spawns 10 concurrent threads attempting to check out a product with only 2 items in stock. Verifies exactly 2 succeed and 8 fail cleanly without negative stock.
   - `CouponConcurrencyTest`: Spawns concurrent requests trying to redeem the same coupon code. Verifies exactly 1 succeeds.
   - `IdempotencyTest`: Fires multiple simultaneous or sequential checkouts with the same `X-Idempotency-Key`. Verifies only 1 order is created and identical responses are returned.

---

## Execution Plan Roadmap

- [x] Initial Spring Boot setup & GitHub remote repository creation
- [x] Project structure re-organization & documentation sync
- [ ] **Step 1**: Create JPA Entities, Enums & Repositories with custom pessimistic lock queries
- [ ] **Step 2**: Implement DTOs, Custom Exceptions, and Global Exception Handler
- [ ] **Step 3**: Implement Core Services (`CartService`, `CheckoutService`, `CouponService`, `AdminService`)
- [ ] **Step 4**: Implement REST Controllers & OpenAPI / API documentation
- [ ] **Step 5**: Write Data Seeder (`DataInitializer`)
- [ ] **Step 6**: Develop Concurrency & Idempotency Integration Tests
- [ ] **Step 7**: Create `DECISIONS.md` with complete rationale & AI log reflection
- [ ] **Step 8**: Commit, push, and verify everything builds cleanly (`mvnw clean test`)
