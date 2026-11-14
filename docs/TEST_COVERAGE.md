# Hopr - Test suite & coverage

## Run
```bash
mvn clean verify                       # tests + JaCoCo report
open target/site/jacoco/index.html     # HTML report (target\site\jacoco\index.html on Windows)
mvn clean verify -Djacoco.minimum=1.00 # fail the build below 100 % line coverage
```
JaCoCo (`pom.xml`) counts **LINE** coverage of `com.hopr.**` and excludes only `dto/**` (records),
`entity/**` (JPA data classes) and `HoprApplication` (the `main` method). Lombok-generated code is
ignored through `lombok.config` (`lombok.addLombokGeneratedAnnotation = true`).
`jacoco.minimum` defaults to `0.00` (report only) so the build never fails by surprise; raise it when you are ready.
If your JDK is older than 25 and JaCoCo complains, use `-Djacoco.version=0.8.13`.

## Which test covers which class
| Production class(es) | Test class(es) |
|---|---|
| `GeoUtil`, `PhoneUtil`, `VehicleMasker`, `AppealCity`, `AppealProfileType` | `GeoAndMaskingTest`, `UtilEdgeCasesTest` |
| `OtpService` | `OtpServiceTest` (all branches), `AuthAndUserApiTest` |
| `JwtService`, `JwtAuthFilter`, `AuthUser` | `JwtServiceAndFilterTest` |
| `DeviceBindingService` | `DeviceBindingServiceTest`, `DeviceBindingAppealMigrationTest` |
| `AppealStorageService` | `AppealStorageServiceTest`, `AppealAdminApiTest` |
| `NotificationService` | `NotificationServiceTest` |
| `PricingService`, `DynamicPricingService`, `DtoMapper`, `HostService.matchLabel` | `PricingMapperAndLabelsTest`, `DynamicPricingServiceTest` |
| `RideMatchingService` | `RideMatchingUnitTest`, `MatchingApiTest`, `MiscIntegrationTest` |
| `TripAccessService` | `TripAccessServiceTest` |
| `ReferralService` | `ReferralServiceTest`, `BookingTripApiTest` |
| `GlobalExceptionHandler` + all exceptions | `GlobalExceptionHandlerTest` + every API test |
| `StompAuthChannelInterceptor`, `ChatWebSocketController` | `StompInterceptorAndControllerTest` |
| `AuthService`, `AuthController` | `AuthAndUserApiTest` |
| `UserService`, `UserController`, `SupportService`, `SupportController` | `AuthAndUserApiTest`, `CompanyChangeAndRelocationTest`, `BookingTripApiTest`, `DeviceBindingAppealMigrationTest` |
| `VehicleService`, `VehicleController`, `RideService`, `RideController` | `VehicleAndRideApiTest`, `CompanyChangeAndRelocationTest` |
| `BookingService`, `BookingChatService`, `BookingController`, `TripService`, `TripController`, `RatingService`, `WalletService`, `WalletController`, `ReferralController` | `BookingTripApiTest`, `BookingConcurrencyAndSettlementTest`, `HostQuickActionIntegrationTest` |
| `HostService`, `HostController` | `HostQuickActionIntegrationTest`, `BookingTripApiTest` |
| `AppealService`, `AppealController`, `AdminAppealController` | `AppealAdminApiTest`, `DeviceBindingAppealMigrationTest` |
| `SecurityConfig`, `WebSocketConfig`, `AppConfig`, `OpenApiConfig`, `HoprProperties`, `DataInitializer` | loaded by every `@SpringBootTest`; `MiscIntegrationTest` re-runs the seeder |

## Honest note
The suite was written without being able to run Maven/JaCoCo in the authoring environment, so a
**measured 100 % cannot be promised**. Run `mvn clean verify`, open the JaCoCo report and send me the
list of red/yellow lines - those are the remaining branches to cover.
