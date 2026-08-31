package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.respond.ReceiptItemDto;
import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.exception.PaymentValidationException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private OrderItemRepo orderItemRepo;

    @Override
    public ReceiptResponse getReceiptData(String paymentReference) {
        Payment payment = paymentRepo.findByReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));

        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new PaymentValidationException("Payment status is not successful for reference: " + paymentReference);
        }

        Order order = payment.getOrder();
        String orderId = order.getOrderId();

        // Fetch OrderItems by orderId using JPA default method + Java stream filtering
        List<OrderItem> orderItems = orderItemRepo.findAll().stream()
                .filter(oi -> oi.getOrder() != null && orderId.equals(oi.getOrder().getOrderId()))
                .collect(Collectors.toList());

        List<ReceiptItemDto> items = orderItems.stream().map(oi -> new ReceiptItemDto(
                oi.getItem() != null ? oi.getItem().getItemName() : "Unknown Item",
                oi.getQuantity(),
                oi.getUnitPrice(),
                oi.getTotalPrice(),
                oi.getSpecialInstructions()
        )).collect(Collectors.toList());

        BigDecimal subtotal = items.stream()
                .map(ReceiptItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String receiptNumber = "REC-" + payment.getReference();
        String tableNumber = order.getTable() != null ? order.getTable().getTableNumber() : "N/A";
        String seatLabel = order.getSeat() != null ? order.getSeat().getSeatNumber() : "N/A";

        return new ReceiptResponse(
                receiptNumber,
                payment.getReference(),
                orderId,
                tableNumber,
                seatLabel,
                payment.getCreatedAt(),
                items,
                subtotal,
                payment.getAmount(),
                payment.getStatus()
        );
    }

    @Override
    public byte[] generateReceiptCsv(String paymentReference) {
        ReceiptResponse data = getReceiptData(paymentReference);

        StringBuilder sb = new StringBuilder();
        sb.append("--- Receipt Metadata ---\n");
        sb.append("Receipt Number,").append(sanitizeForCsv(data.getReceiptNumber())).append("\n");
        sb.append("Payment Reference,").append(sanitizeForCsv(data.getPaymentReference())).append("\n");
        sb.append("Date,").append(data.getPaymentDate() != null ? data.getPaymentDate().toString() : "").append("\n");
        sb.append("Table Number,").append(sanitizeForCsv(data.getTableNumber())).append("\n");
        sb.append("Seat Label,").append(sanitizeForCsv(data.getSeatLabel())).append("\n");
        sb.append("Order ID,").append(sanitizeForCsv(data.getOrderId())).append("\n\n");

        sb.append("--- Line Items ---\n");
        sb.append("Item Name,Quantity,Unit Price,Total Price,Special Instructions\n");
        for (ReceiptItemDto item : data.getItems()) {
            sb.append(sanitizeForCsv(item.getItemName())).append(",")
                    .append(item.getQuantity()).append(",")
                    .append(item.getUnitPrice()).append(",")
                    .append(item.getTotalPrice()).append(",")
                    .append(sanitizeForCsv(item.getSpecialInstructions())).append("\n");
        }
        sb.append("\n");

        sb.append("--- Summary ---\n");
        sb.append("Subtotal,").append(data.getSubtotal()).append("\n");
        sb.append("Total Paid,").append(data.getTotalPaid()).append("\n");
        sb.append("Payment Status,").append(data.getStatus().name()).append("\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String sanitizeForCsv(String val) {
        if (val == null) {
            return "";
        }
        String processed = val;
        if (!processed.isEmpty()) {
            char firstChar = processed.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
                processed = "'" + processed;
            }
        }
        if (processed.contains(",") || processed.contains("\"") || processed.contains("\n") || processed.contains("\r")) {
            processed = "\"" + processed.replace("\"", "\"\"") + "\"";
        }
        return processed;
    }
}
