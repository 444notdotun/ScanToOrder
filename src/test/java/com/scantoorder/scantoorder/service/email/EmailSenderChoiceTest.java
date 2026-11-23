package com.scantoorder.scantoorder.service.email;

import com.scantoorder.scantoorder.service.Interface.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

// Whatever is set, exactly one sender exists: a SendByte key wins, then an SMTP host, then the in-memory outbox.
class EmailSenderChoiceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SendByteEmailSender.class, SmtpEmailSender.class, InMemoryEmailSender.class);

    private void expect(Class<? extends EmailSender> type, String... settings) {
        runner.withPropertyValues(settings).run(context -> {
            assertEquals(1, context.getBeansOfType(EmailSender.class).size(), "exactly one sender for " + String.join(", ", settings));
            assertInstanceOf(type, context.getBean(EmailSender.class));
        });
    }

    @Test
    void testThatNothingSetKeepsEmailsInMemory() {
        expect(InMemoryEmailSender.class);
        expect(InMemoryEmailSender.class, "SENDBYTE_API_KEY=", "MAIL_HOST=");
        expect(InMemoryEmailSender.class, "SENDBYTE_API_KEY=   ", "MAIL_HOST=  ");
    }

    @Test
    void testThatAMailHostAloneMeansSmtp() {
        expect(SmtpEmailSender.class, "MAIL_HOST=smtp.example.com");
        expect(SmtpEmailSender.class, "MAIL_HOST=smtp.example.com", "SENDBYTE_API_KEY=");
    }

    @Test
    void testThatASendByteKeyWinsOverSmtp() {
        expect(SendByteEmailSender.class, "SENDBYTE_API_KEY=sk_test_abc");
        expect(SendByteEmailSender.class, "SENDBYTE_API_KEY=sk_live_abc", "MAIL_HOST=smtp.example.com");
    }
}
