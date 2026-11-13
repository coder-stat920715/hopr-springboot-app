package com.hopr.service;

import com.hopr.config.HoprProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Asynchronous SMS / e-mail / in-app notification gateway (logs when no provider is configured). */
@Service
@Slf4j
public class NotificationService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final HoprProperties props;

    public NotificationService(ObjectProvider<JavaMailSender> mailSender, HoprProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Async
    public void sendSms(String phone, String message) {
        log.info("[SMS -> {}] {}", phone, message);
    }

    @Async
    public void sendEmail(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (props.getMail().isEnabled() && sender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(props.getMail().getFrom());
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                sender.send(msg);
                return;
            } catch (Exception ex) {
                log.warn("Failed to send e-mail to {}: {}", to, ex.getMessage());
            }
        }
        log.info("[EMAIL -> {}] {} | {}", to, subject, body);
    }

    @Async
    public void notifyUser(Long userId, String message) {
        log.info("[PUSH -> user {}] {}", userId, message);
    }
}
