package com.scantoorder.scantoorder.service.email;

import java.time.LocalDateTime;

public record OutboxMessage(String to, String subject, String body, LocalDateTime sentAt) {
}
