package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
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

    // The caller holds the payment row's lock. Returns false when there was nothing to do.
    public boolean success(Payment payment, String gatewayResponse) {
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            return false;
        }
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        if (gatewayResponse != null) {
            payment.setGatewayResponse(gatewayResponse);
        }
        paymentRepo.save(payment);
        Order order = payment.getOrder();
        if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setOrderStatus(OrderStatus.PAID);
            orderRepo.save(order);
        } else if (order.getOrderStatus() == OrderStatus.CANCELED) {
            // The guest left or was closed out before the money arrived. The order never reaches the
            // kitchen, and the guest gets everything back without waiting for a manager.
            refundService.startOrMarkFailed(payment);
        } else {
            log.warn("Payment {} succeeded for order {} which is already {}. It needs a manager's attention.",
                    payment.getReference(), order.getOrderId(), order.getOrderStatus());
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
