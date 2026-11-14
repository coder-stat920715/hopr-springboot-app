package com.hopr.util;

import com.hopr.entity.AppealCity;
import com.hopr.entity.AppealProfileType;
import com.hopr.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilEdgeCasesTest {

    @Test
    void geoRounding() {
        assertThat(GeoUtil.round1(1.26)).isEqualTo(1.3);
        assertThat(GeoUtil.round2(1.256)).isEqualTo(1.26);
    }

    @Test
    void projectionOnZeroLengthSegmentIsStart() {
        GeoUtil.Projection p = GeoUtil.projectOnSegment(17.1, 78.1, 17.0, 78.0, 17.0, 78.0);
        assertThat(p.t()).isZero();
        assertThat(p.lat()).isEqualTo(17.0);
    }

    @Test
    void projectionInsideSegment() {
        GeoUtil.Projection p = GeoUtil.projectOnSegment(17.05, 78.0, 17.0, 78.0, 17.1, 78.0);
        assertThat(p.t()).isBetween(0.49, 0.51);
    }

    @Test
    void phoneNormalisation() {
        assertThat(PhoneUtil.normalize("919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneUtil.normalize("09876543210")).isEqualTo("+919876543210");
        assertThat(PhoneUtil.normalize("9876543210")).isEqualTo("+919876543210");
        assertThatThrownBy(() -> PhoneUtil.normalize(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PhoneUtil.normalize("12345")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void vehicleMaskerEdgeCases() {
        assertThat(VehicleMasker.normalize(null)).isNull();
        assertThat(VehicleMasker.normalize(" ts-07 ab 4821 ")).isEqualTo("TS07AB4821");
        assertThat(VehicleMasker.mask(null)).isEmpty();
        assertThat(VehicleMasker.mask("")).isEmpty();
        assertThat(VehicleMasker.mask("ABC")).isEqualTo("****");
        assertThat(VehicleMasker.mask("XYZ12345678")).isEqualTo("****5678");
        assertThat(VehicleMasker.mask("DL1CAB1234")).isEqualTo("DL-01-****-1234");
    }

    @Test
    void appealCityParsing() {
        assertThat(AppealCity.parse("delhi ncr")).isEqualTo(AppealCity.DELHI_NCR);
        assertThat(AppealCity.parse("Hyderabad")).isEqualTo(AppealCity.HYDERABAD);
        assertThat(AppealCity.KOLKATA.getLabel()).isEqualTo("Kolkata");
        assertThatThrownBy(() -> AppealCity.parse(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AppealCity.parse("  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AppealCity.parse("Atlantis")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void appealProfileTypeParsing() {
        assertThat(AppealProfileType.parse("HOST_RIDE_GIVER")).isEqualTo(AppealProfileType.HOST_RIDE_GIVER);
        assertThat(AppealProfileType.parse("guest - ride taker")).isEqualTo(AppealProfileType.GUEST_RIDE_TAKER);
        assertThat(AppealProfileType.HOST_RIDE_GIVER.getLabel()).isEqualTo("Host - Ride Giver");
        assertThatThrownBy(() -> AppealProfileType.parse(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AppealProfileType.parse("driver")).isInstanceOf(BadRequestException.class);
    }
}
