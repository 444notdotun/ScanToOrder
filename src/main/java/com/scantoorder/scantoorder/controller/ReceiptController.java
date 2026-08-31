package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.Interface.ReceiptService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
            @NotNull(message = "Reference cannot be null") 
            @Pattern(regexp = "^[A-Za-z0-9_-]{5,64}$", message = "Invalid payment reference format")
            @PathVariable String reference) {
        ReceiptResponse response = receiptService.getReceiptData(reference);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(response));
    }

    @GetMapping("/{reference}/download")
    public ResponseEntity<byte[]> downloadReceiptCsv(
            @NotNull(message = "Reference cannot be null") 
            @Pattern(regexp = "^[A-Za-z0-9_-]{5,64}$", message = "Invalid payment reference format")
            @PathVariable String reference) {
        byte[] csvBytes = receiptService.generateReceiptCsv(reference);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt_" + reference + ".csv\"");
        
        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }
}
