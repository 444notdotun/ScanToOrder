package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.Interface.ReceiptService;
import com.scantoorder.scantoorder.dtos.request.EmailReceiptRequest;
import com.scantoorder.scantoorder.utils.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/receipts")
@Validated
public class ReceiptController {

    @Autowired
    private ReceiptService receiptService;

    @GetMapping("/{reference}")
    public ResponseEntity<ApiResponse<ReceiptResponse>> getReceipt(
            @NotNull(message = "We can't find that payment. Go back and try again.") 
            @Pattern(regexp = "^[A-Za-z0-9_-]{5,64}$", message = "We can't find that payment. Go back and try again.")
            @PathVariable String reference) {
        ReceiptResponse response = receiptService.getReceiptData(CurrentTenant.id(), reference);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    // Only when the guest asks, and only to the address they type.
    @PostMapping("/{reference}/email")
    public ResponseEntity<ApiResponse<String>> emailReceipt(
            @NotNull(message = "We can't find that payment. Go back and try again.")
            @Pattern(regexp = "^[A-Za-z0-9_-]{5,64}$", message = "We can't find that payment. Go back and try again.")
            @PathVariable String reference,
            @Valid @RequestBody EmailReceiptRequest request,
            HttpServletRequest http) {
        String answer = receiptService.emailReceipt(CurrentTenant.id(), reference, request.getEmail(), ClientAddress.of(http));
        return ResponseEntity.ok(new ApiResponse<>(answer));
    }
}
