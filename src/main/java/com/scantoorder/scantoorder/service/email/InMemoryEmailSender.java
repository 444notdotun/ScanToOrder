package com.scantoorder.scantoorder.service.email;

import com.scantoorder.scantoorder.service.Interface.EmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used while MAIL_HOST is not set: emails are kept in memory so the platform admin can read them.
 */
@Component
@ConditionalOnExpression("'${MAIL_HOST:}'.isEmpty()")
public class InMemoryEmailSender implements EmailSender {

    private static final int KEEP = 200;
    private final List<OutboxMessage> outbox = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String subject, String body) {
        outbox.add(new OutboxMessage(to, subject, body, LocalDateTime.now()));
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
