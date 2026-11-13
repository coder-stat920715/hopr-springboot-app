package com.hopr.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoAndMaskingTest {

    @Test
    void haversineOneDegreeLatitudeIsAbout111Km() {
        double m = GeoUtil.haversineMeters(17.0, 78.0, 18.0, 78.0);
        assertThat(m).isCloseTo(111_195.0, within(300.0));
    }

    @Test
    void projectionClampsToSegmentEnds() {
        GeoUtil.Projection before = GeoUtil.projectOnSegment(16.9, 78.0, 17.0, 78.0, 17.1, 78.0);
        GeoUtil.Projection after = GeoUtil.projectOnSegment(17.2, 78.0, 17.0, 78.0, 17.1, 78.0);
        assertThat(before.t()).isEqualTo(0.0);
        assertThat(after.t()).isEqualTo(1.0);
    }

    @Test
    void masksRegistrationNumbers() {
        assertThat(VehicleMasker.mask("TS07AB4821")).isEqualTo("TS-07-****-4821");
        assertThat(VehicleMasker.mask("wb 06 cd 9104")).isEqualTo("WB-06-****-9104");
        assertThat(VehicleMasker.mask("WB-06-CD-9104")).isEqualTo("WB-06-****-9104");
    }

    @Test
    void normalisesPhoneNumbers() {
        assertThat(PhoneUtil.normalize("98765 43210")).isEqualTo("+919876543210");
        assertThat(PhoneUtil.normalize("+91 98765-43210")).isEqualTo("+919876543210");
    }
}
