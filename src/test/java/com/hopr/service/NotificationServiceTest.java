package com.hopr.service;

import com.hopr.config.HoprProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> provider;
    @Mock
    private JavaMailSender sender;

    private HoprProperties props;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        props = new HoprProperties();
        service = new NotificationService(provider, props);
    }

    @Test
    void smsAndPushOnlyLog() {
        assertThatCode(() -> {
            service.sendSms("+911", "hello");
            service.notifyUser(1L, "hi");
        }).doesNotThrowAnyException();
    }

    @Test
    void emailDisabledOnlyLogs() {
        when(provider.getIfAvailable()).thenReturn(sender);
        service.sendEmail("a@b.com", "s", "b");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailEnabledWithoutSenderOnlyLogs() {
        props.getMail().setEnabled(true);
        when(provider.getIfAvailable()).thenReturn(null);
        assertThatCode(() -> service.sendEmail("a@b.com", "s", "b")).doesNotThrowAnyException();
    }

    @Test
    void emailEnabledSends() {
        props.getMail().setEnabled(true);
        when(provider.getIfAvailable()).thenReturn(sender);
        service.sendEmail("a@b.com", "s", "b");
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailFailureFallsBackToLog() {
        props.getMail().setEnabled(true);
        when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> service.sendEmail("a@b.com", "s", "b")).doesNotThrowAnyException();
    }
}
