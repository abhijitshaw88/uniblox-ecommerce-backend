# Ecommerce Backend Implementation Plan

We will build the ecommerce backend using Java 21, Spring Boot, Spring Data JPA, and an H2 in-memory database to fulfill the assignment requirements.

## User Review Required
Please review the proposed API design and data model below. Specifically, the concurrency and idempotency strategies are critical for this assignment.

## Open Questions
1. **Money Representation**: I propose using `BigDecimal` for all currency calculations to prevent floating-point errors. Does this sound good?
2. **Idempotency Strategy for Checkout**: I propose using an `idempotency_key` header provided by the client during checkout. The system will store this key. If a retry happens with the same key, it will return the existing order rather than creating a new one.

## Proposed Changes

### 1. Data Model
We will use JPA entities for the following tables:
- `Product`: `id`, `name`, `price`, `available_inventory`
- `Cart`: `id`
- `CartItem`: `id`, `cart_id`, `product_id`, `quantity` (Note: we fetch the latest price dynamically during checkout and cart view to handle price changes).
- `Order`: `id`, `cart_id`, `gross_total`, `discount_amount`, `net_total`, `coupon_id`
- `OrderItem`: `id`, `order_id`, `product_id`, `quantity`, `price_at_purchase`
- `Coupon`: `id`, `code`, `discount_percentage`, `is_redeemed`, `milestone_index`
- `IdempotencyKey`: `key`, `order_id` (To prevent duplicate checkouts)

### 2. Concurrency & Inventory Control
- **Database Locks**: We will use pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the `Product` entity when a checkout occurs. This ensures that concurrent checkouts for the same product do not oversell inventory.
- **Coupons**: Pessimistic locking will also be used on the `Coupon` entity during checkout to ensure a single coupon cannot be redeemed by two concurrent threads.

### 3. API Endpoints
**Cart API:**
- `POST /carts` - Create a new cart.
- `GET /carts/{id}` - View cart, including dynamic total calculation based on current prices.
- `POST /carts/{id}/items` - Add product.
- `PUT /carts/{id}/items/{productId}` - Update quantity.
- `DELETE /carts/{id}/items/{productId}` - Remove item.

**Checkout API:**
- `POST /checkout` - Expects `{ "cartId": 1, "couponCode": "DISC10" }` and an `Idempotency-Key` header. Validates inventory, creates `Order`, applies discount, deducts inventory, and marks coupon as used.

**Admin API:**
- `POST /admin/coupons/generate` - Checks if total successful orders modulo `n` == 0 and generates a coupon for that milestone.
- `GET /admin/report` - Returns metrics by aggregating `Order` and `OrderItem` data.

### 4. Milestone Tracking
We will maintain a simple configuration or calculate the milestone dynamically by counting total successful orders.

## Verification Plan

### Automated Tests
We will write JUnit 5 and Spring Boot Test integration tests:
- **Happy Paths**: Cart lifecycle, checkout, coupon generation.
- **Concurrency Tests**: Use `ExecutorService` to fire multiple simultaneous checkout requests for the same cart/coupon/product to ensure inventory isn't oversold and coupons aren't double-redeemed.
- **Idempotency Tests**: Fire the same checkout request with the same idempotency key twice to ensure only one order is created.

### Manual Verification
- Run the Spring Boot application and manually test endpoints using `curl` or Postman.
- Verify `DECISIONS.md` is populated accurately.
