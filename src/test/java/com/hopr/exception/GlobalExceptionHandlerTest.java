package com.hopr.exception;

import com.hopr.config.HoprProperties;
import com.hopr.dto.CommonDtos.CompanyLockedResponse;
import com.hopr.dto.CommonDtos.DeviceLimitErrorResponse;
import com.hopr.dto.CommonDtos.ErrorResponse;
import com.hopr.entity.Ride;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new HoprProperties());

    @SuppressWarnings("unused")
    void dummy(String value) {
    }

    private BeanPropertyBindingResult bindingResult() {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
        br.addError(new FieldError("obj", "phone", "must not be blank"));
        return br;
    }

    private void assertError(ResponseEntity<ErrorResponse> r, HttpStatus status, String message) {
        assertThat(r.getStatusCode()).isEqualTo(status);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().status()).isEqualTo(status.value());
        if (message != null) {
            assertThat(r.getBody().message()).contains(message);
        }
    }

    @Test
    void simpleStatusMappings() {
        assertError(handler.notFound(new ResourceNotFoundException("nf")), HttpStatus.NOT_FOUND, "nf");
        assertError(handler.badRequest(new BadRequestException("br")), HttpStatus.BAD_REQUEST, "br");
        assertError(handler.conflict(new ConflictException("cf")), HttpStatus.CONFLICT, "cf");
        assertError(handler.forbidden(new ForbiddenException("fb")), HttpStatus.FORBIDDEN, "fb");
        assertError(handler.forbidden(new AccessDeniedException("denied")), HttpStatus.FORBIDDEN, "denied");
        assertError(handler.unauthorized(new UnauthorizedException("ua")), HttpStatus.UNAUTHORIZED, "ua");
        assertError(handler.generic(new RuntimeException("boom")), HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected");
    }

    @Test
    void persistenceErrors() {
        assertError(handler.optimisticLock(new ObjectOptimisticLockingFailureException(Ride.class, 1L)),
                HttpStatus.CONFLICT, "retry");
        assertError(handler.integrity(new DataIntegrityViolationException("dup")), HttpStatus.CONFLICT, "conflicts");
    }

    @Test
    void validationErrors() throws Exception {
        MethodParameter param = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        ResponseEntity<ErrorResponse> r = handler.validation(new MethodArgumentNotValidException(param, bindingResult()));
        assertError(r, HttpStatus.BAD_REQUEST, "Validation failed");
        assertThat(r.getBody().fieldErrors()).containsEntry("phone", "must not be blank");

        ResponseEntity<ErrorResponse> bind = handler.bindFailure(new BindException(bindingResult()));
        assertThat(bind.getBody().fieldErrors()).containsKey("phone");

        assertError(handler.constraint(new ConstraintViolationException("bad value", Set.of())), HttpStatus.BAD_REQUEST, "bad value");
        assertError(handler.typeMismatch(new MethodArgumentTypeMismatchException("x", Long.class, "id", param,
                new RuntimeException())), HttpStatus.BAD_REQUEST, "'id'");
    }

    @Test
    void deviceLimitBody() {
        ResponseEntity<DeviceLimitErrorResponse> r = handler.deviceLimit(new DeviceAccountLimitException());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        DeviceLimitErrorResponse b = r.getBody();
        assertThat(b.errorCode()).isEqualTo("DEVICE_ACCOUNT_LIMIT_REACHED");
        assertThat(b.ctaButtonText()).isEqualTo("Submit an appeal");
        assertThat(b.externalAppealUrl()).isEqualTo("https://survey.zohopublic.in/zs/ekBH9S");
        assertThat(b.internalAppealEndpoint()).isEqualTo("/api/v1/appeals/submit");
        assertThat(b.appealWebFormUrl()).isEqualTo("/appeal-form.html");
    }

    @Test
    void companyLockedBody() {
        CompanyUpdateLockedException ex = new CompanyUpdateLockedException("locked", Map.of("registeredName", "X"));
        assertThat(ex.getPrefilledAppeal()).containsKey("registeredName");
        ResponseEntity<CompanyLockedResponse> r = handler.companyLocked(ex);
        assertThat(r.getStatusCode().value()).isEqualTo(423);
        assertThat(r.getBody().errorCode()).isEqualTo("COMPANY_UPDATE_LOCKED");
        assertThat(r.getBody().prefilledAppeal()).containsEntry("registeredName", "X");
    }
}
