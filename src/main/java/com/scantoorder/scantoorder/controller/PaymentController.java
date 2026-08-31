package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<InitializePaymentResponse>> initializePayment(
            @RequestBody @Valid  InitializePaymentRequest request) {
        InitializePaymentResponse response = paymentService.initializePayment(request);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @GetMapping("/verify/{reference}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> verifyPayment(
            @NotNull(message = "Reference cannot be null") 
            @Pattern(regexp = "^[A-Za-z0-9_-]{5,64}$", message = "Invalid payment reference format")
            @PathVariable String reference) {
        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(reference);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @NotNull(message = "Payload cannot be null") 
            @RequestParam (required = false)String payload,
            @NotNull(message = "Signature header cannot be null" )
            @RequestHeader(value = "x-paystack-signature",required = false) String signatureHeader) {
        paymentService.handleWebhook(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
