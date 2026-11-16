package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import com.scantoorder.scantoorder.service.email.OutboxMessage;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Only exists while MAIL_HOST is not set: it shows the emails that would have been sent.
 */
@RestController
@RequestMapping("/api/v1/platform/outbox")
@ConditionalOnExpression("'${MAIL_HOST:}'.isEmpty()")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class OutboxController {

    @Autowired
    private InMemoryEmailSender emailSender;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OutboxMessage>>> outbox() {
        return ResponseEntity.ok(new ApiResponse<>(emailSender.messages()));
    }
}
