# Scan to Order

A production-grade QR-based restaurant ordering system. Customers scan a table QR code, claim a seat, browse the menu, place orders, and pay — all from their phone browser with no app download required. Kitchen staff receive orders in real time and update status through a dedicated kitchen display interface.

---

## What it does

A customer walks into a restaurant, scans the QR code on their table, selects their seat, browses the active menu, builds their cart, and submits an order. Payment must clear before the kitchen sees the order. The chef marks the order through its lifecycle. A waiter delivers the food. When the session ends, the table resets automatically.

Multiple customers at the same table can place independent orders on separate tabs. Each order is linked to a specific seat within the dining session.

---

## Architecture

```
Customer Browser
    └── REST API (Spring Boot)
            ├── PostgreSQL — orders, sessions, payments, workers
            ├── Redis — menu caching (cache-aside, 24hr TTL)
            └── Scheduled Jobs — end of day cleanup, stale session reset
```

Monolith. Single deployable JAR. No message queue for MVP. Scheduled jobs handle async cleanup.

---

## Domain Model

**Floor and Seating**
- `RestaurantTable` — physical table with capacity and QR code generation. Status derived from seat states via syncStatusFromSeats(), never set manually.
- `Seat` — individual seat at a table (VACANT, HELD, OCCUPIED)
- `DiningSession` — represents the active group at a table, decoupled from the physical table (ACTIVE or CLOSED)
- `DiningSessionSeat` — join table linking seats to a session with foreign keys on both sides

**Menu Catalog**
- `Menu` — singleton, only one active menu at a time
- `Category` — belongs to a menu, groups related items
- `Item` — menu item with price and availability toggle (86 toggle for out-of-stock)

**Order and Payment**
- `Order` — linked to a dining session and a specific seat
- `OrderItem` — line item with quantity, unit price snapshot, subtotal snapshot, and special instructions
- `Payment` — separate from order, multiple payment attempts allowed per order, only one can be SUCCESS

**Operations and Feedback**
- `Worker` — single entity with role enum: WAITER, CHEF, MANAGER
- `ServiceCall` — customer assistance request with assignment and resolution tracking
- `Review` — one review per dining session, rating 1 to 5

---

## Order State Machine

```
PENDING_PAYMENT
    → payment attempted
    → SUCCESS → PAID → PREPARING → READY → DELIVERED
    → FAILED → diner retries → PENDING_PAYMENT
    → CANCELLED (manual or end of day scheduler)
```

No order reaches the kitchen without confirmed payment.

---

## Payment Flow

Payment is required before kitchen dispatch. Multiple payment methods supported: CARD, BANK_TRANSFER, CASH. Each attempt creates a new Payment record. If payment fails, the order stays in PENDING_PAYMENT and the customer retries.

Paystack webhook is verified using HMAC SHA512 on every incoming event. The backend acknowledges with 200 immediately before processing to prevent Paystack retries.

---

## Scheduled Jobs

**End of day cleanup** — cancels all orders stuck in PENDING_PAYMENT at midnight. Handles abandoned orders where the customer left without paying. Silent, no notification sent.

**Stale READY auto-complete** — marks orders in READY status as DELIVERED after 90 minutes if no waiter has manually updated the status. Safety net for busy service periods.

---

## Worker Roles

All staff use a single `workers` table with a role enum.

| Role | Permissions |
|---|---|
| MANAGER | Full access: menu management, table setup, order oversight, worker management |
| CHEF | Kitchen display: view PAID orders, update to PREPARING and READY |
| WAITER | Floor operations: mark DELIVERED, handle service calls, release ghost seats, close sessions |

---

## API Reference

### Public — no token required

| Method | Endpoint | Description |
|---|---|---|
| GET | /api/tables/{tableId}/seat-map | View table info and seat availability |
| POST | /api/seats/{seatId}/claim | Claim a seat, receive customer JWT |
| POST | /api/workers/login | Worker login, receive worker JWT |
| POST | /webhooks/paystack | Paystack payment webhook (HMAC SHA512 verified) |

### Customer — customer JWT required

| Method | Endpoint | Description |
|---|---|---|
| GET | /api/menu | Browse active menu with categories and items |
| POST | /api/orders | Submit cart and create order |
| GET | /api/orders/{id} | Poll order status |
| POST | /api/orders/{id}/pay | Initiate payment |
| POST | /api/service-calls | Request waiter assistance |
| GET | /api/sessions/table-view | View all seats and orders at the table |
| POST | /api/sessions/{id}/review | Submit session review (one per session) |

### Kitchen — CHEF role required

| Method | Endpoint | Description |
|---|---|---|
| GET | /kitchen/orders | View all PAID orders |
| PATCH | /kitchen/orders/{id}/status | Update order to PREPARING or READY |

### Waiter — WAITER role required

| Method | Endpoint | Description |
|---|---|---|
| GET | /api/orders/ready | View all READY orders |
| PATCH | /api/orders/{id}/deliver | Mark order as DELIVERED |
| GET | /api/service-calls | View open service calls |
| PATCH | /api/service-calls/{id} | Claim and resolve service call |
| POST | /api/seats/{id}/release | Release ghost seat back to VACANT |
| POST | /api/sessions/{id}/close | Close dining session and reset table |

### Manager — MANAGER role required

| Method | Endpoint | Description |
|---|---|---|
| POST | /api/menu | Create menu (singleton enforcement) |
| POST | /api/categories | Create category |
| POST | /api/items | Create item |
| PATCH | /api/items/{id}/toggle | Toggle item availability (86 toggle) |
| POST | /api/tables | Create table and its seats |
| GET | /api/tables | List all tables with status |
| GET | /api/tables/{id}/qrcode | Generate QR code for table |
| GET | /api/orders | View all orders with status filter |
| GET | /api/service-calls | View all service calls |
| GET | /api/workers | List all workers |
| POST | /api/workers | Create a new worker account |
| PATCH | /api/workers/{id} | Update worker details |

---

## Auth Design

One JWT secret, two token types distinguished by a `type` claim.

**Customer JWT** — issued on seat claim, set as HttpOnly cookie
- type: CUSTOMER
- sessionId, seatId, tableId
- Expiry: 4 hours

**Worker JWT** — issued on login
- type: WORKER
- workerId, role
- Expiry: 8 hours

The JWT filter reads the `type` claim and builds the appropriate principal. `@AuthenticationPrincipal CustomerPrincipal` injects sessionId into customer endpoints. `@AuthenticationPrincipal WorkerPrincipal` injects workerId and role into worker endpoints. The sessionId is never accepted from the request body.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL |
| Caching | Redis — menu cache with 24hr TTL, cache-aside pattern |
| ORM | Spring Data JPA with HikariCP |
| Auth | JWT — one secret, two token types (CUSTOMER and WORKER) |
| Payment | Paystack with HMAC SHA512 webhook verification |
| Scheduler | Spring @Scheduled |
| Deployment | Render |
| CI/CD | GitHub Actions — test gate → build → deploy |

---

## Running Locally

**Prerequisites:** Java 21, PostgreSQL, Redis, Maven

```bash
# Clone the repo
git clone https://github.com/444notdotun/scan-to-order.git
cd scan-to-order

# Create the database
psql -U postgres -c "CREATE DATABASE scan_to_order;"

# Start Redis
redis-server

# Configure environment variables
cp .env.example .env
# Fill in DATABASE_URL, JWT_SECRET, PAYSTACK_SECRET_KEY

# Run the app
./mvnw spring-boot:run
```

App runs on `http://localhost:8080`.

---

## Environment Variables

| Variable | Description |
|---|---|
| DATABASE_URL | PostgreSQL connection string |
| JWT_SECRET | Secret for signing all JWTs. One secret, two token types distinguished by a `type` claim. Customer tokens carry sessionId, seatId, tableId with 4-hour expiry. Worker tokens carry workerId and role with 8-hour expiry. |
| PAYSTACK_SECRET_KEY | Paystack secret key for HMAC SHA512 webhook verification |
| MANAGER_USERNAME | Manager account username, seeded on startup |
| MANAGER_PASSWORD | Manager account password, BCrypt hashed on seed |
| REDIS_HOST | Redis host. Defaults to localhost in development. |
| REDIS_PORT | Redis port. Defaults to 6379 in development. |
| ENVIRONMENT | development or production |
| PORT | Injected by Render. Spring Boot reads from ${PORT:8080} |

---

## CI/CD

GitHub Actions pipeline runs on every push to main:

1. Spin up PostgreSQL and Redis service containers
2. Run tests against the containers — pipeline fails if any test fails
3. Build JAR
4. Render auto-deploys on push to main after tests pass

No broken code reaches production.

---

## Status

Active build. Deployed at [scantoorder.onrender.com](https://scantoorder.onrender.com).

*This README is updated as the build progresses.*
