# SlotSure

**Hyperlocal delivery-slot booking with capacity management, flexible payments, and delivery-secured settlement.**

SlotSure enables customers to schedule delivery of an already-purchased item by selecting a convenient one-hour slot for their location. It combines location-based slot availability, capacity-controlled reservations, agent dispatch, and payment orchestration—including prepaid, COD with online payment, and COD with cash payment. Prepaid funds remain secured until successful delivery, coordinated through Java microservices and event-driven architecture.

## The problem

Customers often receive deliveries within a broad window such as “sometime today.” Choosing a delivery slot introduces two challenges:

- **Capacity:** concurrent bookings must never overbook the same slot.
- **Consistency:** payment, slot confirmation, and delivery span multiple services without a shared database transaction.

SlotSure addresses these with atomic slot holds and an orchestrated Saga.

## Architecture

![SlotSure architecture](docs/architecture.svg)

| Service | Port | Responsibility |
|---|---:|---|
| slot-service | 8081 | Slots, capacity, holds, H3 areas |
| order-service | 8082 | Orders, Saga orchestration, notifications |
| payment-service | 8083 | Payment gateways, escrow ledger, COD |
| dispatch-service | 8084 | Agents and delivery assignment |
| Mobile app | 8088 | Customer app and admin dashboard |

**Infrastructure:** MySQL 8 · Redis 7 · Apache Kafka (KRaft) · Docker Compose · Kafka UI (`localhost:8090`)

**Stack:** Java 17 · Spring Boot 3.3 · Spring Kafka · Spring Data JPA/Redis · MySQL · Redis Lua · Uber H3 · React Native · Expo · TypeScript

## How It Works

1. Customer selects a delivery location on the map.
2. Available one-hour delivery slots are displayed based on location and capacity.
3. Customer selects a slot, which is temporarily held for 5 minutes.
4. Customer places the order and chooses a payment method:
   - **Prepaid** — Payment is collected upfront and held in escrow until successful delivery.
   - **COD (Online Payment)** — Customer chooses Cash on Delivery but pays online during checkout.
   - **COD (Cash Payment)** — Customer pays cash directly to the delivery agent upon delivery.
5. Order, slot reservation, and payment are coordinated through the Saga orchestration flow.
6. A delivery agent is assigned based on availability and workload.
7. After successful delivery, prepaid funds are released from escrow. COD payments are recorded according to the selected payment method.
8. If any step fails, the system compensates by releasing the reserved slot and handling the corresponding payment state.

## Key engineering decisions

- **No overbooking:** atomic Redis Lua hold + database optimistic locking.
- **Saga orchestration:** order-service coordinates cross-service state and compensation.
- **Idempotency:** REST idempotency keys and Kafka processed-event tracking.
- **Payment fallback:** primary gateway failure routes to another gateway.
- **Escrow ledger:** append-only double-entry ledger; every movement balances.
- **Hyperlocal planning:** H3 resolution 9 booking cells (~175 m) and resolution 8 zones (~460 m).
- **Event-driven communication:** Kafka; services own their databases.

## Test results

| Test | Result |
|---|---|
| 500 concurrent requests for 3 slot places | **3 succeeded; 497 rejected; no overbooking** |
| Duplicate hold with same idempotency key | Same hold; capacity decremented once |
| Hold expiry | Capacity restored |
| Both payment gateways fail | Order failed; slot released; retry available |
| Prepaid, online and COD flows | Passed end-to-end |
| Settlement ledger | **Difference = 0** |
| Customer data isolation | Verified |
| Unit tests | slot: 68 · order: 63 · payment: 28 · dispatch: 24 |

## Run locally

**Prerequisites:** Docker, Java 17, Maven, Node 18+

```bash
# Start infrastructure
docker compose up -d

# Build
mvn clean verify

# Run each service in a separate terminal
mvn -f slot-service/pom.xml spring-boot:run
mvn -f order-service/pom.xml spring-boot:run
mvn -f payment-service/pom.xml spring-boot:run
mvn -f dispatch-service/pom.xml spring-boot:run
```

### Run scenarios

```bash
node scripts/scenarios.mjs all
```

Available scenarios: `prepaid`, `cash`, `online`, `idem`, `invalid`, `ledger`, `fail`, `all`.

Kafka UI: http://localhost:8090

### Mobile app

```bash
cd mobile
npm install

# Set your PC's LAN IP (for a physical phone)
set EXPO_PUBLIC_API_HOST=192.168.1.20

npx expo start --port 8088
```

Optional: configure `EXPO_PUBLIC_GOOGLE_MAPS_KEY`.

Demo logins: `user1@slotsure.demo`, `user2@slotsure.demo`, `user3@slotsure.demo`, `admin@slotsure.demo`.

## Known limitations

- Kafka/MySQL dual-write risk; Transactional Outbox is planned.
- Single Kafka broker.
- Demo authentication; APIs are not secured yet.
- Notifications use polling; FCM push is not implemented.
- Live agent tracking and refunds are on the roadmap.

## Roadmap

- [ ] Transactional Outbox
- [ ] API Gateway and real authentication
- [ ] FCM push notifications
- [ ] WebSocket live availability and agent tracking
- [ ] H3 demand heatmap
- [ ] Resilience4j, Testcontainers, k6
- [ ] Prometheus, Grafana and GitHub Actions

---

**Built to explore distributed systems, concurrency, event-driven architecture, payment safety, and hyperlocal delivery planning.**

© 2026 Rajasree Singamsetty. All rights reserved.
