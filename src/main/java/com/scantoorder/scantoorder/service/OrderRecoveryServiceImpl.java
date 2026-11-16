package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.UnavailableReason;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.SwapRequest;
import com.scantoorder.scantoorder.dtos.respond.OrderActionResponse;
import com.scantoorder.scantoorder.dtos.respond.RefundQueueResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.OrderNotFoundException;
import com.scantoorder.scantoorder.exception.OrderStatusException;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.OrderItemService;
import com.scantoorder.scantoorder.service.Interface.OrderRecoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderRecoveryServiceImpl implements OrderRecoveryService {

    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private RefundService refundService;

    @Transactional
    @Override
    public OrderActionResponse markUnavailable(String tenantId, String orderId, UnavailableReason reason) {
        Order order = lock(tenantId, orderId);
        if (order.getOrderStatus() != OrderStatus.PAID && order.getOrderStatus() != OrderStatus.PREPARING) {
            throw new OrderStatusException("The kitchen can only give up on an order that is paid or being cooked.");
        }
        order.setOrderStatus(OrderStatus.UNAVAILABLE);
        order.setUnavailableReason(reason);
        orderRepo.save(order);
        if (reason == UnavailableReason.RAN_OUT) {
            for (OrderItem line : activeLines(orderId)) {
                Item item = line.getItem();
                if (item != null && item.isAvailable()) {
                    item.setAvailable(false);
                    itemRepo.save(item);
                }
            }
            menuService.clearCache(tenantId);
        }
        return action(order, "A waiter will go to the table.");
    }

    @Transactional
    @Override
    public OrderActionResponse swap(String tenantId, String orderId, SwapRequest request) {
        Order order = lock(tenantId, orderId);
        if (order.getOrderStatus() != OrderStatus.UNAVAILABLE) {
            throw new OrderStatusException("Only an order the kitchen could not make can be swapped.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestException("Choose what the guest will have instead.");
        }
        // Everything is checked before anything changes.
        for (CreateOrderItemRequest wanted : request.getItems()) {
            Item item = itemRepo.findByTenant_TenantIdAndItemName(tenantId, wanted.getItemName())
                    .orElseThrow(() -> new ConflictException("We cannot find " + wanted.getItemName() + " on the menu."));
            if (!item.isAvailable()) {
                throw new ConflictException(item.getItemName() + " is out of stock. Choose something else.");
            }
            if (wanted.getQuantity() < 1) {
                throw new InvalidRequestException("Choose at least one " + item.getItemName() + ".");
            }
        }
        BigDecimal was = BigDecimal.ZERO;
        for (OrderItem line : activeLines(orderId)) {
            was = was.add(line.getTotalPrice());
            line.setSwappedOut(true);
            orderItemRepo.save(line);
        }
        BigDecimal now = BigDecimal.ZERO;
        for (CreateOrderItemRequest wanted : request.getItems()) {
            now = now.add(orderItemService.createOrderItem(tenantId, orderId, wanted).getTotalPrice());
        }
        // No money moves in the app: the difference is only written down.
        String note = String.format("Swapped at the table: was NGN %,.2f, now NGN %,.2f.", was, now);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            note += " " + request.getNote().trim();
        }
        order.setNote(order.getNote() == null ? note : order.getNote() + " " + note);
        order.setOrderStatus(OrderStatus.PAID);
        order.setSwappedAt(LocalDateTime.now());
        orderRepo.save(order);
        return action(order, "The kitchen has a new ticket for this order.");
    }

    @Transactional
    @Override
    public OrderActionResponse requestRefund(String tenantId, String orderId) {
        Order order = lock(tenantId, orderId);
        if (order.getOrderStatus() != OrderStatus.UNAVAILABLE) {
            throw new OrderStatusException("A refund can only be asked for on an order the kitchen could not make.");
        }
        order.setOrderStatus(OrderStatus.REFUND_REQUESTED);
        orderRepo.save(order);
        return action(order, "The manager will approve the refund.");
    }

    @Transactional(readOnly = true)
    @Override
    public RefundQueueResponse refundQueue(String tenantId) {
        RefundQueueResponse response = new RefundQueueResponse();
        for (Order order : orderRepo.findByTenant_TenantIdAndOrderStatusOrderByUpdatedAtAsc(tenantId, OrderStatus.REFUND_REQUESTED)) {
            response.getRequests().add(new RefundQueueResponse.Request(
                    order.getOrderId(), order.getOrderNumber(),
                    order.getTable() == null ? null : order.getTable().getTableNumber(),
                    order.getSeat() == null ? null : order.getSeat().getSeatNumber(),
                    order.getSessionSeat() == null ? null : order.getSessionSeat().getGuestName(),
                    order.getTotalAmount(),
                    order.getUnavailableReason() == null ? null : order.getUnavailableReason().name(),
                    order.getUpdatedAt()));
        }
        for (Payment payment : paymentRepo.findByTenant_TenantIdAndStatusOrderByUpdatedAtAsc(tenantId, PaymentStatus.REFUND_FAILED)) {
            response.getFailedRefunds().add(new RefundQueueResponse.Failed(
                    payment.getOrder().getOrderId(), payment.getOrder().getOrderNumber(),
                    payment.getReference(), payment.getAmount(), payment.getUpdatedAt()));
        }
        return response;
    }

    // Two cases share this door: a manager approving a requested refund, and a manager retrying one Paystack could not complete.
    @Transactional
    @Override
    public OrderActionResponse approveRefund(String tenantId, String orderId) {
        Order order = lock(tenantId, orderId);
        List<Payment> payments = paymentRepo.findByOrder_OrderIdAndStatusIn(orderId,
                List.of(PaymentStatus.SUCCESSFUL, PaymentStatus.REFUND_FAILED, PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED));
        if (payments.isEmpty()) {
            throw new OrderStatusException("There is no payment on this order to refund.");
        }
        Payment payment = paymentRepo.findForUpdateByReference(payments.get(0).getReference())
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (payment.getStatus() == PaymentStatus.REFUND_FAILED) {
            refundService.start(payment);
            return action(order, "The refund has been sent to Paystack again.");
        }
        if (payment.getStatus() == PaymentStatus.REFUND_PENDING || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new OrderStatusException("This order's refund is already on its way.");
        }
        if (order.getOrderStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new OrderStatusException("A refund can only be approved once a waiter has asked for it.");
        }
        // Paystack is asked first: if it cannot start the refund, nothing here changes.
        refundService.start(payment);
        order.setOrderStatus(OrderStatus.REFUNDED);
        orderRepo.save(order);
        return action(order, "The refund is on its way to the guest.");
    }

    private Order lock(String tenantId, String orderId) {
        return orderRepo.findForUpdate(orderId, tenantId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }

    private List<OrderItem> activeLines(String orderId) {
        return orderItemRepo.findByOrder_OrderId(orderId).stream().filter(line -> !line.isSwappedOut()).toList();
    }

    private OrderActionResponse action(Order order, String message) {
        return new OrderActionResponse(order.getOrderId(), order.getOrderNumber(), order.getOrderStatus().name(), message);
    }
}
