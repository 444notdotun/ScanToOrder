package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.respond.ReceiptItemDto;
import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.Refund;
import com.scantoorder.scantoorder.data.model.RefundStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.exception.PaymentValidationException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.TryAgainLaterException;
import com.scantoorder.scantoorder.service.Interface.ReceiptService;
import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.service.email.EmailTemplate;
import com.scantoorder.scantoorder.service.email.Mailer;
import com.scantoorder.scantoorder.utils.TimeText;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private RefundService refundService;
    @Autowired
    private Mailer mailer;
    @Autowired
    private RateLimiter limiter;

    public static final String SENT = "Sent. Check your inbox in a minute.";
    public static final String BAD_EMAIL = "That email doesn't look right.";
    static final int PER_RECEIPT = 3;
    static final int PER_ADDRESS = 20;
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Override
    public ReceiptResponse getReceiptData(String tenantId, String paymentReference) {
        Payment payment = paymentRepo.findByReferenceAndTenant_TenantId(paymentReference, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that receipt"));

        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new PaymentValidationException("Payment status is not successful for reference: " + paymentReference);
        }

        Order order = payment.getOrder();
        String orderId = order.getOrderId();

        // Fetch OrderItems by orderId using JPA default method + Java stream filtering
        List<OrderItem> orderItems = orderItemRepo.findByOrder_OrderId(orderId).stream().filter(item -> !item.isSwappedOut()).toList();

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

        Refund refund = refundRepo.findByPayment_Id(payment.getId()).orElse(null);
        String refundMessage = refund == null ? null : refundWording(refund, payment.getTenant().getName());

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
                payment.getStatus(),
                refundMessage,
                refund == null || refund.getStatus() == RefundStatus.CONFIRMED ? null : refundService.linkFor(refund)
        );
    }

    @Override
    public String emailReceipt(String tenantId, String paymentReference, String rawEmail, String address) {
        // The receipt is read first: someone else's reference is a plain "not found" and never counts against anyone.
        ReceiptResponse data = getReceiptData(tenantId, paymentReference);
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.length() > 200 || !EMAIL.matcher(email).matches()) {
            throw new com.scantoorder.scantoorder.exception.InvalidRequestException(BAD_EMAIL);
        }
        Duration hour = Duration.ofHours(1);
        boolean receiptOk = limiter.allow("receipt", paymentReference, PER_RECEIPT, hour);
        boolean addressOk = limiter.allow("receipt-address", address == null ? "unknown" : address, PER_ADDRESS, hour);
        if (!receiptOk || !addressOk) {
            throw new TryAgainLaterException(HttpStatus.TOO_MANY_REQUESTS, "That's a few receipts already. Try again in an hour.");
        }
        Payment payment = paymentRepo.findByReferenceAndTenant_TenantId(paymentReference, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that receipt"));
        String business = payment.getTenant().getName();
        LocalDateTime when = payment.getPaidAt() != null ? payment.getPaidAt() : data.getPaymentDate();
        java.util.List<EmailTemplate.Row> lines = data.getItems().stream()
                .map(item -> new EmailTemplate.Row(item.getQuantity() + " × " + item.getItemName(), naira(item.getTotalPrice())))
                .toList();
        java.util.List<EmailTemplate.Row> facts = new java.util.ArrayList<>();
        facts.add(new EmailTemplate.Row("Receipt", data.getReceiptNumber()));
        if (data.getTableNumber() != null && !"N/A".equals(data.getTableNumber())) {
            facts.add(new EmailTemplate.Row("Table", data.getTableNumber()));
        }
        if (when != null) {
            facts.add(new EmailTemplate.Row("Date", TimeText.dayAndClock(when)));
        }
        EmailTemplate.Rendered body = EmailTemplate.of("Your receipt", business)
                .paragraph("Thanks for eating with us. Here's what you had.")
                .details(facts)
                .lines(lines, "Total", naira(data.getTotalPaid()))
                .small("Paid through Paystack" + (when == null ? "" : " · " + TimeText.dayAndClock(when)))
                .small("Something wrong with your order? Reply to this email and we'll pass it to " + business + ".")
                .render();
        String key = "receipt:" + paymentReference + ":" + email.toLowerCase(java.util.Locale.ROOT) + ":" + System.currentTimeMillis() / 3_600_000L;
        boolean sent = mailer.send(EmailMessage.of(email, "Your receipt from " + business, body, "receipt", key));
        if (!sent) {
            throw new TryAgainLaterException(HttpStatus.SERVICE_UNAVAILABLE, "We couldn't send it just now. Try again in a minute.");
        }
        return SENT;
    }

    private static String naira(BigDecimal amount) {
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        return String.format(java.util.Locale.US, whole ? "₦%,.0f" : "₦%,.2f", amount);
    }

    private String refundWording(Refund refund, String restaurant) {
        String amount = Money.naira(refund.getAmountKobo());
        if (refund.getStatus() == RefundStatus.DUE) {
            return "Refund of " + amount + " due from " + restaurant;
        }
        LocalDateTime sentOn = refund.getSettledAt() != null ? refund.getSettledAt() : refund.getGuestConfirmedAt();
        return "Refund of " + amount + " sent by " + restaurant + " on " + sentOn.toLocalDate();
    }
}
