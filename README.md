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
            └── Scheduled Jobs — end of day cleanup, stale session reset
```

Monolith. Single deployable. No message queue for MVP. Scheduled jobs handle async cleanup.

---

## Domain Model

**Floor and Seating**
- `RestaurantTable` — physical table with capacity and QR code generation
- `Seat` — individual seat at a table (VACANT, HELD, OCCUPIED)
- `DiningSession` — represents the active group at a table, decoupled from the physical table
- `DiningSessionSeat` — join table linking seats to a session

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

Payment is required before kitchen dispatch. Multiple payment methods supported: CARD, BANK_TRANSFER, CASH. Each attempt creates a new Payment record. If payment fails, the order stays in PENDING_PAYMENT and the customer retries. End of day scheduler cancels all orders still in PENDING_PAYMENT.

---

## Scheduled Jobs

Two jobs run automatically:

**End of day cleanup** — cancels all orders stuck in PENDING_PAYMENT at midnight. Handles abandoned orders where the customer left without paying.

**Stale READY auto-complete** — marks orders in READY status as DELIVERED after 90 minutes if no waiter has manually updated the status. Safety net for busy service periods.

---

## Worker Roles

All staff use a single `workers` table with a role enum.

| Role | Permissions |
|---|---|
| MANAGER | Full access: menu management, table setup, order oversight, worker management |
| CHEF | Kitchen display: view PAID orders, update to PREPARING and READY |
| WAITER | Floor operations: mark DELIVERED, handle service calls, release ghost seats |

---

## API Overview

**Customer (no authentication required, sessionToken from seat claim)**

| Method | Endpoint | Description |
|---|---|---|
| GET | /api/tables/{tableId}/seat-map | View table and seat availability |
| POST | /api/seats/{seatId}/claim | Claim a seat, receive sessionToken |
| GET | /api/menu | Browse active menu |
| POST | /api/orders | Submit cart and create order |
| POST | /api/orders/{id}/pay | Initiate payment |
| POST | /api/orders/{id}/review | Submit session review |
| POST | /api/service-calls | Request waiter assistance |

**Kitchen (CHEF role)**

| Method | Endpoint | Description |
|---|---|---|
| GET | /kitchen/orders | View all PAID orders |
| PATCH | /kitchen/orders/{id}/status | Update order to PREPARING or READY |

**Management (MANAGER role)**

| Method | Endpoint | Description |
|---|---|---|
| POST | /api/menu | Create menu (singleton) |
| POST | /api/categories | Create category |
| POST | /api/items | Create item |
| PATCH | /api/items/{id}/toggle | Toggle item availability (86) |
| POST | /api/tables | Create table |
| GET | /api/tables | List all tables |
| GET | /api/orders | View all orders with filters |
| GET | /api/service-calls | View all service calls |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL |
| ORM | Spring Data JPA with HikariCP |
| Auth | JWT — one secret, two token types. Customer token embeds sessionId, seatId, tableId. Worker token embeds workerId and role. Backend extracts sessionId from token claim, never from request body. |
| Scheduler | Spring @Scheduled |
| Deployment | Render |
| CI/CD | GitHub Actions — test gate → build → deploy |

---

## Running Locally

**Prerequisites:** Java 21, PostgreSQL, Maven

```bash
# Clone the repo
git clone https://github.com/444notdotun/scan-to-order.git
cd scan-to-order

# Create the database
psql -U postgres -c "CREATE DATABASE scan_to_order;"

# Configure environment variables
cp .env.example .env
# Fill in DATABASE_URL, JWT_SECRET, PAYSTACK_SECRET_KEY

# Run the app
./mvnw spring-boot:run
```

App runs on `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

---

## Environment Variables

| Variable | Description |
|---|---|
| DATABASE_URL | PostgreSQL connection string |
| JWT_SECRET | Secret for signing all JWTs. One secret, two token types distinguished by a `type` claim. Customer tokens carry sessionId, seatId, tableId, and a 4-hour expiry matching a meal duration. Worker tokens carry workerId, role, and an 8-hour expiry matching a shift. |
| PAYSTACK_SECRET_KEY | Paystack secret key for webhook verification |
| ENVIRONMENT | development or production |

---

## CI/CD

GitHub Actions pipeline runs on every push to main:

1. Run tests — pipeline fails if any test fails
2. Build JAR
3. Deploy to Render

No broken code reaches production.

---

## Status

Active build. Deployment pending.

*This README is updated as the build progresses.*