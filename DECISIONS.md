# Architecture and Design Decisions

## System Invariants
1. **Inventory cannot be negative**: The `available_inventory` of a product must never fall below 0 under concurrent purchases. Enforced via Pessimistic Write locks during checkout.
2. **Carts are Single-Use**: A cart's status transitions from `OPEN` to `CHECKED_OUT`. Once checked out, items cannot be added/removed, and it cannot be checked out again.
3. **Idempotent Checkouts**: Retrying a checkout request with the same `X-Idempotency-Key` yields the exact existing order result without recreating the order, deducting inventory twice, or consuming additional coupons.
4. **Coupons are Single-Use**: A coupon can only be redeemed once. This is enforced with a database lock to prevent simultaneous concurrent checkouts from double-redeeming.
5. **Exact Monetary Math**: All currency fields and calculations are performed using `BigDecimal` with explicit scale (2 decimal places) and `RoundingMode.HALF_UP` to strictly avoid floating-point inaccuracies.

## Ambiguities & Trade-offs
- **Price Changes Before Checkout**: 
  - *Ambiguity*: Should cart totals reflect prices when the item was added, or current live prices?
  - *Resolution*: Carts dynamically compute their totals using the *current live product prices* when viewed or checked out. Only upon successful checkout is the price "snapshot" taken and saved immutably to the `OrderItem`. This prevents a customer from exploiting an old price if the system raises it before they check out.
- **Payment Abstraction**: 
  - *Ambiguity*: How should payment be represented?
  - *Resolution*: The `checkout()` method intrinsically assumes payment success. If an actual payment gateway were integrated, it would be called synchronously right before saving the `Order` entity. If the payment failed, the entire `@Transactional` checkout method would roll back, safely releasing the inventory and coupon locks.

## Decision: Concurrency Control Strategy

**Context:** Concurrent checkouts attempting to buy the same limited-inventory product could lead to overselling (race condition).

**Options considered:** 
1. *Optimistic Locking*: Using a `@Version` field. Fast for reads, but throws exceptions under high contention, causing a poor user experience as checkouts randomly fail and require retries.
2. *Pessimistic Locking*: Locking the database row so threads queue up sequentially.

**Choice:** Pessimistic Write Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`).

**Why:** E-commerce inventory is a classic high-contention resource (e.g., flash sales). Preventing overselling is a hard requirement. Pessimistic locking ensures threads wait in line to deduct inventory rather than failing aggressively. It perfectly matches the strong invariant requirement.

**Consequences:** Easier to guarantee correctness. Harder to scale horizontally across multiple databases without a distributed lock (like Redis), but scales perfectly well in a single monolithic RDBMS.

## Decision: Idempotency Strategy

**Context:** Clients may timeout during checkout and retry the request. A retry must not charge them twice.

**Options considered:**
1. Deduplicate by `cart_id`.
2. Explicit `X-Idempotency-Key` header stored in an idempotency table.

**Choice:** Explicit `IdempotencyRecord` table keyed by a unique UUID header.

**Why:** Depending purely on `cart_id` is risky if the business later decides a user can reuse a cart or if we allow "partial checkouts". An explicit key allows the client to deterministically define a retry attempt.

**Consequences:** Easier for clients to retry safely. Harder because it requires an extra database insert on every successful checkout and forces the API client to generate UUIDs.

## Decision: Money and Rounding

**Context:** Floating point variables (`double`, `float`) introduce rounding errors (e.g., `0.1 + 0.2 = 0.30000000000000004`), which is unacceptable for financial data.

**Choice:** Used `java.math.BigDecimal` exclusively.

**Why:** It is the industry standard for precise financial calculations in Java. 

**Consequences:** Easier to ensure precision. Harder/more verbose to read and write the mathematical operations (e.g., using `.multiply()` instead of `*`).

## Decision: Coupon Milestone Generation

**Context:** The requirement says "after every *n*th successfully placed order, make a discount coupon available."

**Options considered:**
1. *Auto-generate*: The `CheckoutService` evaluates `total_orders % n == 0` and synchronously generates a coupon during checkout.
2. *Admin-triggered*: An administrator explicitly hits a `/generate` endpoint. 

**Choice:** Admin-triggered (via `POST /api/admin/coupons/generate`).

**Why:** The instructions stated: "An administrator can request coupon generation." This implies it should be a distinct action. The logic calculates `total_successful_orders / n` to determine the highest eligible milestone, and blocks duplicate generation for that milestone.

**Consequences:** Safer, decoupled from the critical checkout path. However, requires an external CRON job or human admin to actually make the coupons available to users.

## Decision: Structural API Security

**Context:** The API design requires differentiating administrative endpoints from customer endpoints without implementing actual authentication.

**Choice:** Segregated all admin operations into an `AdminController` under the `/api/admin/**` URL pattern.

**Why:** This natively satisfies the requirement of "clearly identifying" administrative operations while respecting the directive to skip real authentication implementation. 

**Consequences:** Easily evolvable into a secure application later simply by attaching a Spring Security filter matching `/api/admin/**` requiring an `ADMIN` role.

---

## AI Usage (Antigravity Agent)

I utilized Google's Antigravity agent as a pair-programming partner to accelerate this assignment while maintaining strict control over the architecture and final deliverables.

**How I directed the AI:**
1. **Architecture First**: Before allowing the AI to write any code, I had it parse the `README.md` and generate a detailed implementation plan. I reviewed this plan to ensure the Pessimistic Locking and Idempotency strategies met my standards.
2. **Structural Control**: I commanded the AI to segregate the workspace, forcefully moving the generated Spring Boot files out of the instruction directory into a clean `uniblox-ecommerce-backend` root folder to maintain proper Git history.
3. **Test-Driven Verification**: I instructed the AI to write high-concurrency integration tests using `ExecutorService` and `CountDownLatch` to mathematically prove the locking strategy worked before finalizing the code.

**Correction/Refusal Example:** 
During the setup phase, the AI generated an invalid Spring Boot parent version and misconfigured the web dependencies (`spring-boot-starter-webmvc` instead of `spring-boot-starter-web`), causing compilation failures. Furthermore, the AI attempted to automatically commit files that I did not want in the final repository (such as a private commit history log). I explicitly intervened, rejected the AI's automated `git commit` commands via the Antigravity permission prompts, forced it to correct the `pom.xml` dependencies, and instructed it to carefully stage only the necessary source code. This ensured the repository remained clean and strictly adhered to my requirements.

## Future Evolution for Production Scale
If we had to scale this across multiple application instances and a production distributed database (e.g., Postgres cluster):
1. **Distributed Locks**: Database pessimistic locks (`SELECT ... FOR UPDATE`) are fine, but in a highly distributed microservices environment, we would migrate to Redis-backed distributed locks (e.g., Redisson) for cross-service inventory and coupon locking.
2. **Asynchronous Checkouts**: Instead of synchronous checkouts, we would drop the checkout request onto a Kafka queue and return an `ACCEPTED` status, letting a highly optimized worker pool process the queue linearly to prevent database connection exhaustion during flash sales.

## Next 2 Hours Focus
If given another two hours, I would:
1. Implement a comprehensive input validation framework (e.g., handling extreme quantities or null coupon codes cleanly).
2. Wire up a Swagger/OpenAPI UI for easier testing and documentation visualization.
3. Migrate the in-memory H2 database to a Dockerized PostgreSQL instance with Flyway migrations.
 
 # #   D e c i s i o n :   A P I   R a t e   L i m i t i n g   ( R e s i l i e n c e 4 j )  
 * * C o n t e x t : * *   E - c o m m e r c e   c h e c k o u t   e n d p o i n t s   a r e   p r i m e   t a r g e t s   f o r   b r u t e - f o r c e   s c r i p t i n g   a n d   D D O S   a t t a c k s .  
 * * C h o i c e : * *   I m p l e m e n t e d   a p p l i c a t i o n - l e v e l   r a t e   l i m i t i n g   u s i n g   R e s i l i e n c e 4 j   a n d   S p r i n g   A O P   d i r e c t l y   o n   t h e   C h e c k o u t   C o n t r o l l e r .  
 * * W h y : * *   W h i l e   u s u a l l y   h a n d l e d   b y   a n   A P I   G a t e w a y ,   a d d i n g   i t   t o   t h e   a p p l i c a t i o n   l o g i c   d e m o n s t r a t e s   d e f e n s i v e   p r o g r a m m i n g   a n d   p r o t e c t s   t h e   d a t a b a s e   c o n n e c t i o n   p o o l   f r o m   m a l i c i o u s   c h e c k o u t   s p a m .  
 * * C o n s e q u e n c e s : * *   I f   t h e   l i m i t   ( 5   r e q u e s t s   p e r   s e c o n d )   i s   b r e a c h e d ,   t h e   f a l l b a c k   m e t h o d   i n s t a n t l y   r e t u r n s   a n   R F C - 7 8 0 7   c o m p l i a n t   \ 4 2 9   T o o   M a n y   R e q u e s t s \   e r r o r   w i t h o u t   h i t t i n g   t h e   d a t a b a s e .  
 