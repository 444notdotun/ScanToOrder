package com.scantoorder.scantoorder.service.email;

import java.time.LocalDateTime;

// The text version of an email, as the platform owner reads it while no real sender is set.
public record OutboxMessage(String to, String subject, String body, String tag, LocalDateTime sentAt) {
}
