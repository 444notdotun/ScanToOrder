package com.scantoorder.scantoorder.service.email;

import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * How the app sends an email. A failed send is a short line in the log and nothing more: it never undoes what the person did,
 * and the address and the text are never logged.
 */
@Slf4j
@Component
public class Mailer {

    @Autowired
    private EmailSender sender;

    // Sends now. True when the email was handed over.
    public boolean send(EmailMessage message) {
        try {
            sender.send(message);
            return true;
        } catch (EmailSendException e) {
            log.warn("An email ({}) could not be sent", message.tag());
            return false;
        }
    }

    // Sends once the change in progress is safely saved, so a rolled-back change never sends a link to nothing.
    // With no change in progress it sends at once.
    public void sendAfterCommit(EmailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message);
                }
            });
        } else {
            send(message);
        }
    }
}
