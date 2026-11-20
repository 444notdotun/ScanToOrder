package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.RefundReason;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * What a successful payment does. The one place both the verify call and the webhook go through,
 * so a second notice for the same payment changes nothing.
 */
@Slf4j
@Component
public class PaymentSettlement {

    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private RefundService refundService;
    @Autowired
    private LiveUpdates liveUpdates;

    // The caller holds the payment row's lock. Returns false when there was nothing to do.
    public boolean success(Payment payment, String gatewayResponse) {
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            return false;
        }
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(java.time.LocalDateTime.now());
        if (gatewayResponse != null) {
            payment.setGatewayResponse(gatewayResponse);
        }
        paymentRepo.save(payment);
        Order order = payment.getOrder();
        if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT
                && order.getTotalAmount() != null
                && payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            // The checkout was priced before the order changed. The money came in, but it is not this
            // order's total, so the kitchen does not see it and the restaurant owes it back.
            log.warn("Payment {} does not match the current order total. The order stays unpaid.", payment.getReference());
            refundService.createDue(payment, RefundReason.AMOUNT_CHANGED);
        } else if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setOrderStatus(OrderStatus.PAID);
            order.setPaidAt(java.time.LocalDateTime.now());
            orderRepo.save(order);
            liveUpdates.publish(order.getTenant().getTenantId(),
                    order.getDinningSession() == null ? null : order.getDinningSession().getSessionId(),
                    "order", order.getOrderId(), "PAID");
        } else if (order.getOrderStatus() == OrderStatus.CANCELED) {
            // The guest left or was closed out before the money arrived. The order never reaches the
            // kitchen, and the restaurant owes the guest all of it back.
            refundService.createDue(payment, RefundReason.LATE_PAYMENT);
        } else {
            // The order was already paid for: this is a second payment, and the restaurant owes it back.
            refundService.createDue(payment, RefundReason.DOUBLE_PAYMENT);
        }
        return true;
    }

    public boolean failure(Payment payment, String gatewayResponse) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        payment.setStatus(PaymentStatus.FAILED);
        if (gatewayResponse != null) {
            payment.setGatewayResponse(gatewayResponse);
        }
        paymentRepo.save(payment);
        return true;
    }
}
