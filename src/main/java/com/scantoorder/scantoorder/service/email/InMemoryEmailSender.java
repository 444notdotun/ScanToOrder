package com.scantoorder.scantoorder.service.email;

import com.scantoorder.scantoorder.service.Interface.EmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used while neither SENDBYTE_API_KEY nor MAIL_HOST is set: emails are kept in memory so the platform admin can read them.
 */
@Component
@ConditionalOnExpression(EmailSenderChoice.IN_MEMORY)
public class InMemoryEmailSender implements EmailSender {

    private static final int KEEP = 200;
    private final List<OutboxMessage> outbox = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(EmailMessage message) {
        outbox.add(new OutboxMessage(message.to(), message.subject(), message.text(), message.tag(), LocalDateTime.now()));
        while (outbox.size() > KEEP) {
            outbox.remove(0);
        }
    }

    public List<OutboxMessage> messages() {
        synchronized (outbox) {
            List<OutboxMessage> copy = new ArrayList<>(outbox);
            Collections.reverse(copy);
            return copy;
        }
    }

    public void clear() {
        outbox.clear();
    }
}
