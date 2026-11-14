package com.hopr.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "hopr")
@Getter
@Setter
public class HoprProperties {

    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private Mail mail = new Mail();
    private Pricing pricing = new Pricing();
    private Matching matching = new Matching();
    private Planner planner = new Planner();
    private Cancellation cancellation = new Cancellation();
    private Referral referral = new Referral();
    private Seed seed = new Seed();
    private Security security = new Security();
    private Appeals appeals = new Appeals();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "hopr-super-secret-signing-key-change-me-in-production-0123456789";
        private long accessTokenMinutes = 120;
        private long refreshTokenDays = 14;
    }

    @Getter
    @Setter
    public static class Otp {
        private int length = 6;
        private int ttlMinutes = 5;
        private int maxAttempts = 5;
        /** When non-blank, every OTP equals this value (development only). */
        private String fixedCode = "";
        /** When true the OTP is echoed in the API response (development only). */
        private boolean exposeInResponse = false;
    }

    @Getter
    @Setter
    public static class Mail {
        private boolean enabled = false;
        private String from = "no-reply@hopr.app";
    }

    @Getter
    @Setter
    public static class Pricing {
        private BigDecimal carRatePerKm = new BigDecimal("8.00");
        private BigDecimal bikeRatePerKm = new BigDecimal("4.00");
        private int minimumFare = 10;
        private int discountPercent = 25;
        private int platformFeePercent = 5;
        private Dynamic dynamic = new Dynamic();
    }

    /** Surge / dynamic fuel-share pricing knobs. */
    @Getter
    @Setter
    public static class Dynamic {
        private boolean enabled = true;
        private int peakSurchargePercent = 10;
        private int offPeakDiscountPercent = 5;
        private int maxDetourSurchargePercent = 8;
        private int scarcitySurchargePercent = 5;
    }

    @Getter
    @Setter
    public static class Security {
        /** Number of distinct phone numbers that may be actively bound to one physical device. */
        private int maxAccountsPerDevice = 1;
    }

    @Getter
    @Setter
    public static class Appeals {
        private String externalFormUrl = "https://survey.zohopublic.in/zs/ekBH9S";
        private String uploadDir = "uploads/appeals";
        private int maxAttachments = 5;
        private long maxFileSizeBytes = 5L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Matching {
        private int defaultWindowMinutes = 30;
        private int maxWalkMeters = 1500;
        private int minMatchPercentage = 50;
        private double roadFactor = 1.25;
        private double averageSpeedKmph = 25;
        private int nearbyPickupMeters = 500;
        private int pickupWindowMinutes = 10;
        private boolean excludePastRides = true;
    }

    @Getter
    @Setter
    public static class Planner {
        private int horizonDays = 7;
    }

    @Getter
    @Setter
    public static class Cancellation {
        private int freeWindowMinutes = 30;
        private int penaltyPercent = 20;
    }

    @Getter
    @Setter
    public static class Referral {
        private BigDecimal referrerReward = new BigDecimal("100");
        private BigDecimal refereeReward = new BigDecimal("50");
        private BigDecimal lifetimeCap = new BigDecimal("10000");
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = false;
    }
}
