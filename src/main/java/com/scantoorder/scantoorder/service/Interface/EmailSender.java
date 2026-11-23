package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.service.email.EmailMessage;

public interface EmailSender {
    // Throws EmailSendException when the message could not be handed over.
    void send(EmailMessage message);
}
