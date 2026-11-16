package com.scantoorder.scantoorder.service.email;

import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@ConditionalOnExpression("!'${MAIL_HOST:}'.isEmpty()")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    private final String from;

    public SmtpEmailSender(@Value("${MAIL_HOST}") String host,
                           @Value("${MAIL_PORT:587}") int port,
                           @Value("${MAIL_USERNAME:}") String username,
                           @Value("${MAIL_PASSWORD:}") String password,
                           @Value("${MAIL_FROM:no-reply@plately.local}") String from) {
        this.from = from;
        mailSender.setHost(host);
        mailSender.setPort(port);
        if (!username.isBlank()) {
            mailSender.setUsername(username);
            mailSender.setPassword(password);
        }
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // The message holds a password, so only the failure is reported, never its text.
            throw new EmailSendException("The email could not be sent");
        }
    }
}
