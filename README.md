# Hopr – Daily Office Carpooling & Bikepooling Backend

Spring Boot 3.5 · Java 21 · Spring Security (JWT) · Spring Data JPA · WebSocket (STOMP) · H2 (dev) / PostgreSQL (prod) · springdoc-openapi

## Run
```bash
mvn spring-boot:run                 # dev profile: in-memory H2, seeded demo data, OTP is always 123456
mvn test                            # unit + MockMvc + concurrency tests
docker compose up --build           # prod profile + PostgreSQL
```
Swagger UI: http://localhost:8080/swagger-ui.html · Postman: `postman/Hopr.postman_collection.json`

## Demo logins (dev, OTP `123456`)
| Phone | Who |
|---|---|
| +919000000000 | Admin |
| +919000000001 | Ravi Kumar – Hyderabad car host (Capgemini, TS-07-****-4821) |
| +919000000002 | Sneha Reddy – Hyderabad bike host (TCS) |
| +919000000003 | Arjun Mehta – Hyderabad guest (Capgemini) |
| +919000000004 | Debojit Banerjee – Kolkata bike host (Cognizant) – use for the relocation flow |
| +919000000005 | Subhajit Sen – Kolkata car host |
| +919000000006 | Ananya Roy – Kolkata guest |

Flow: `POST /api/v1/auth/send-otp` → `POST /api/v1/auth/verify-otp` → use `accessToken` as `Authorization: Bearer ...`.

## Key behaviours
* **Matching** (`GET /api/v1/rides/matches`): guest start/end are projected on the host route; pickup/drop walk = Haversine to those points, shared km = route between them, `match% = shared / (shared + walk)`. Tags: `> 90% match`, `Nearby Pickup` (≤ 500 m), `Carry`. Filters: date, time ± window, `carOnly`, city.
* **Booking**: guest request (PENDING) → host `PATCH /bookings/{id}/respond`. Accept decrements seats under `@Version` optimistic locking (no overbooking) and moves the discounted fare into wallet **escrow**. Ride completion pays the host (minus platform fee); host cancellation refunds fully; late guest cancellation costs a penalty.
* **Company change**: `company-change/initiate` → badge `PENDING_REVERIFICATION` → `verify` (OTP) → new company, green badge. Admin override: `POST /support/company-reset`. City move: `POST /rides/migrate-city-schedules`.
* **Trip**: STOMP chat (`/ws`, send `/app/trips/{id}/chat`, subscribe `/topic/trips/{id}`, pass `Authorization: Bearer` header on CONNECT), masked calls, live location + ETA, mutual ratings.
* Vehicle registrations are always masked publicly (`TS-07-****-4821`).

Configuration lives in `application.yml` (`hopr.*`: pricing, matching, cancellation, referral caps, OTP). In prod set `HOPR_JWT_SECRET`, `DB_*`, and SMTP via `SPRING_MAIL_*` + `HOPR_MAIL_ENABLED=true`.
