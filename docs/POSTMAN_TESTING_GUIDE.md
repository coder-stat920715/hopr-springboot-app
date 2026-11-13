# Hopr – Postman Testing Guide

Files: `postman/Hopr.postman_collection.json` (131 requests, each with step-by-step description + assertions) and `postman/Hopr.postman_environment.json` (`baseUrl`).

## 1. Prerequisites
1. Java 21 + Maven. Start the API on the **dev** profile (default): `mvn spring-boot:run`
   - In-memory H2, demo data seeded, OTP is always **123456**, OTP is echoed as `devOtp`.
2. Postman desktop (or Newman). Import the collection and the environment, select **Hopr – Local**.
3. **Restart the app before every full run** – wallets, bookings and ride state are cumulative.

## 2. How to run
**Collection Runner:** right-click the collection → *Run collection* → keep the folder order → *Run*. All tests should pass.
**Newman:** `newman run postman/Hopr.postman_collection.json -e postman/Hopr.postman_environment.json`
**Manual:** open a request, read its *Description* (Purpose / Steps / Expected), press *Send*, then read the *Test Results* tab.

Collection pre-request script computes IST dates into `{{today}}`, `{{tomorrow}}`, `{{d2}}`, `{{d3}}`, `{{d4}}`. Test scripts store ids/tokens in collection variables (`{{arjunToken}}`, `{{lifeRideId}}`, `{{lifeBookingId}}` …) so requests chain automatically.

## 3. Demo accounts (seeded)
| Key | Phone | Role |
|---|---|---|
| admin | +919000000000 | ADMIN |
| ravi | +919000000001 | Hyderabad car host (TS07AB4821 → `TS-07-****-4821`) |
| sneha | +919000000002 | Hyderabad bike host |
| arjun | +919000000003 | Hyderabad guest, wallet ₹1500 |
| debojit | +919000000004 | Kolkata bike host (Cognizant) – relocation demo |
| subhajit | +919000000005 | Kolkata car host |
| ananya | +919000000006 | Kolkata guest |
| (new) | +919000000099 | created by folder 5 |

## 4. Folder-by-folder plan
| Folder | What it proves | Key expected results |
|---|---|---|
| **0 Setup** | OTP login for 7 users | Tokens saved; each response 200 |
| **1 Matching** | Geospatial match cards & filters | Ravi first, match > 90 %, tags `> 90% match` / `Nearby Pickup` / `Carry`; carOnly hides bikes; time window ±10 min hides Sneha; Kolkata guest sees only Kolkata; incomplete coordinates → 400 |
| **2 Booking lifecycle** | request → accept → escrow → trip → payout → rating | PENDING → CONFIRMED; wallet balance −fare, escrow +fare; seatsLeft 2→1; chat, masked call (+9180…), live location + ETA; complete → escrow 0, host +fare−5 %; ratings 201, duplicate 409; non-host actions 403 |
| **3 Cancellation** | refunds & penalty | Free guest cancel → full refund + seat back; reject keeps wallet; host cancel → full refund; booking cancelled ride → 409; cancel < 30 min before departure → 20 % penalty to host |
| **4 Host planner** | templates, edit one day, swap, vehicles | Weekday templates; edit sets `customized`; in-place swap OK / 409 with bookings; CREATE_RETURN makes a new ride; bike with 2 seats → 400; duplicate reg → 409; single active vehicle |
| **5 Onboarding** | new user, KYC, wallet, referral | newUser true; HOST mode w/o vehicle 400; template w/o licence 403; licence OK → 201; top-up 500; referral bonus → 550; 2nd code 400 |
| **6 Support/Admin** | tickets & company reset | User 403 on admin endpoints; admin resolves ticket; reset → badge NONE; fresh TCS verification → GREEN |
| **7 Company change** | Cognizant-Kolkata → Capgemini-Hyderabad | non-allow-listed 400; PENDING_REVERIFICATION (amber); wrong OTP 400; correct OTP → Capgemini GREEN; migrate → 2 archived, 2 new HYDERABAD templates, rides cancelled; Ananya's pending booking cancelled |
| **8 Security** | auth & validation | 401 (no/garbage token), 400 validation with `fieldErrors`, 404, 403, refresh-token rules |

Order matters: **0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8** (folder 7 cancels Debojit's Kolkata rides, so run searches in folder 1 first).

## 5. Manual scenarios not automatable in Postman
### 5.1 Real-time chat & live location (STOMP over WebSocket)
Use any STOMP client (e.g. `wscat`, a browser STOMP.js page, or Postman's WebSocket tab with raw frames).
1. Connect to `ws://localhost:8080/ws` (SockJS: `http://localhost:8080/ws-sockjs`).
2. Send CONNECT with the JWT of a **trip participant** (copy `{{arjunToken}}` after folder 2.9):
```
CONNECT
accept-version:1.2
Authorization:Bearer <accessToken>

^@
```
3. Subscribe: `SUBSCRIBE id:s1 destination:/topic/trips/<lifeRideId>` (and `/topic/trips/<id>/location`). A non-participant gets an error frame.
4. Send: `SEND destination:/app/trips/<lifeRideId>/chat` with body `{"content":"Hello"}`.
5. Expected: all subscribers receive the message JSON; it also appears in `GET /api/v1/trips/{id}/chat`. Run `POST /trips/{id}/location` (2.19) while IN_PROGRESS to see location frames.

### 5.2 Concurrent "last seat" race
Postman cannot fire parallel requests deterministically; run the automated test instead:
`mvn -Dtest=BookingConcurrencyAndSettlementTest test` → 5 guests race for 1 seat; exactly one is CONFIRMED, others get 409.
Manual variant: create a 1-seat ride, create 2 PENDING requests with different guests, then send both ACCEPT calls (`PATCH /bookings/{id}/respond`) at the same moment from two Postman tabs / Newman instances – one returns 200, the other 409.

### 5.3 Prod profile (PostgreSQL)
`docker compose up --build`. OTPs are random and only logged; to keep the demo flow set `HOPR_OTP_FIXEDCODE=123456` and `HOPR_OTP_EXPOSEINRESPONSE=true` in `docker-compose.yml`.

## 6. Troubleshooting
| Symptom | Cause / fix |
|---|---|
| Login says "No OTP requested" | Pre-request `send-otp` did not run – send `POST /auth/send-otp` manually, then verify |
| 401 on everything | Token expired (2 h) – re-run folder 0 |
| Folder 1 returns no rides | Date variables wrong or app not freshly seeded – restart app, check `{{tomorrow}}` |
| 2.4 card not found | Ravi's ride (2.2) failed – ensure 2.1 ran and `{{raviVehicleId}}` is set |
| 3.15–3.21 fail near midnight IST | Ride departs after midnight; rerun at another time |
| Folder 7 `cancelledRides` < 2 | Debojit's rides already archived by an earlier run – restart app |
| 409 on 2.5/3.x | Leftover state from a previous run – restart app |
