package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.OtpPurpose;
import com.hopr.entity.OtpToken;
import com.hopr.exception.BadRequestException;
import com.hopr.repository.OtpTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    private OtpTokenRepository repo;

    private HoprProperties props;
    private OtpService service;

    @BeforeEach
    void setUp() {
        props = new HoprProperties();
        service = new OtpService(repo, props);
    }

    private OtpToken token(String code, Instant expires, int attempts) {
        return OtpToken.builder().target("t").purpose(OtpPurpose.LOGIN).code(code).expiresAt(expires)
                .attempts(attempts).build();
    }

    private void stub(OtpToken t) {
        when(repo.findFirstByTargetAndPurposeAndUsedFalseOrderByCreatedAtDescIdDesc("t", OtpPurpose.LOGIN))
                .thenReturn(Optional.ofNullable(t));
    }

    @Test
    void issuesRandomSixDigitCode() {
        String code = service.issue("t", OtpPurpose.LOGIN);
        assertThat(code).matches("\\d{6}");
        ArgumentCaptor<OtpToken> cap = ArgumentCaptor.forClass(OtpToken.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getCode()).isEqualTo(code);
        assertThat(cap.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void issuesFixedCodeWhenConfigured() {
        props.getOtp().setFixedCode("654321");
        assertThat(service.issue("t", OtpPurpose.LOGIN)).isEqualTo("654321");
    }

    @Test
    void verifyWithoutRequestFails() {
        stub(null);
        assertThatThrownBy(() -> service.verify("t", OtpPurpose.LOGIN, "1"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("No OTP requested");
    }

    @Test
    void expiredOtpIsRejectedAndConsumed() {
        OtpToken t = token("123456", Instant.now().minusSeconds(5), 0);
        stub(t);
        assertThatThrownBy(() -> service.verify("t", OtpPurpose.LOGIN, "123456"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("expired");
        assertThat(t.isUsed()).isTrue();
        verify(repo).save(any(OtpToken.class));
    }

    @Test
    void tooManyAttemptsAreRejected() {
        OtpToken t = token("123456", Instant.now().plusSeconds(60), props.getOtp().getMaxAttempts());
        stub(t);
        assertThatThrownBy(() -> service.verify("t", OtpPurpose.LOGIN, "123456"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Too many");
        assertThat(t.isUsed()).isTrue();
    }

    @Test
    void wrongCodeIncrementsAttempts() {
        OtpToken t = token("123456", Instant.now().plusSeconds(60), 0);
        stub(t);
        assertThatThrownBy(() -> service.verify("t", OtpPurpose.LOGIN, "000000"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Incorrect");
        assertThat(t.getAttempts()).isEqualTo(1);
        assertThat(t.isUsed()).isFalse();
    }

    @Test
    void correctCodeConsumesToken() {
        OtpToken t = token("123456", Instant.now().plusSeconds(60), 0);
        stub(t);
        service.verify("t", OtpPurpose.LOGIN, "123456");
        assertThat(t.isUsed()).isTrue();
    }
}
