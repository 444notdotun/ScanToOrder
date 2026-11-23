package com.scantoorder.scantoorder.service.email;

import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * The second choice: used when MAIL_HOST is set and there is no SendByte key. Sends text and html together, with a Reply-To.
 */
@Component
@ConditionalOnExpression(EmailSenderChoice.SMTP)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    private final String from;
    private final String replyTo;

    public SmtpEmailSender(@Value("${MAIL_HOST}") String host,
                           @Value("${MAIL_PORT:587}") int port,
                           @Value("${MAIL_USERNAME:}") String username,
                           @Value("${MAIL_PASSWORD:}") String password,
                           @Value("${MAIL_FROM:Plately <notify@platelyy.com>}") String from,
                           @Value("${MAIL_REPLY_TO:support@platelyy.com}") String replyTo) {
        this.from = from;
        this.replyTo = replyTo;
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
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setReplyTo(replyTo);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
        } catch (Exception e) {
            // The message can hold a password or a link, so only the failure is reported, never its text.
            throw new EmailSendException("The email could not be sent");
        }
    }
}
