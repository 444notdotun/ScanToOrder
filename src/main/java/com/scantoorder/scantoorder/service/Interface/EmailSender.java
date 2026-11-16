package com.scantoorder.scantoorder.service.Interface;

public interface EmailSender {
    // Throws EmailSendException when the message could not be handed over.
    void send(String to, String subject, String body);
}
