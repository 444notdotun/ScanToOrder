package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.OrderActionResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.OrderNotFoundException;
import com.scantoorder.scantoorder.exception.OrderStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * An order moves forward one step at a time: PAID, PREPARING, READY, DELIVERED. The order row is locked
 * first and the step checked after, so when two staff tap the same order the first change wins and the
 * second is told who got there first.
 */
@Service
public class OrderStepService {

    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private LiveUpdates liveUpdates;

    @Transactional
    public OrderActionResponse start(String tenantId, String workerId, String orderId) {
        Order order = lock(tenantId, orderId);
        OrderStatus now = order.getOrderStatus();
        if (now == OrderStatus.PREPARING || now == OrderStatus.READY || now == OrderStatus.DELIVERED) {
            throw new ConflictException(who(order.getPreparingBy()) + " has already started " + order.getOrderNumber() + ".");
        }
        if (now != OrderStatus.PAID) {
            throw refused(order, "started");
        }
        order.setOrderStatus(OrderStatus.PREPARING);
        order.setPreparingAt(LocalDateTime.now());
        order.setPreparingBy(label(workerId));
        return saved(order, order.getOrderNumber() + " is cooking.");
    }

    @Transactional
    public OrderActionResponse ready(String tenantId, String workerId, String orderId) {
        Order order = lock(tenantId, orderId);
        OrderStatus now = order.getOrderStatus();
        if (now == OrderStatus.READY || now == OrderStatus.DELIVERED) {
            throw new ConflictException(who(order.getReadyBy()) + " has already marked " + order.getOrderNumber() + " ready.");
        }
        if (now == OrderStatus.PAID) {
            throw new OrderStatusException(order.getOrderNumber() + " has not been started yet. Start cooking it first.");
        }
        if (now != OrderStatus.PREPARING) {
            throw refused(order, "marked ready");
        }
        order.setOrderStatus(OrderStatus.READY);
        order.setReadyAt(LocalDateTime.now());
        order.setReadyBy(label(workerId));
        return saved(order, order.getOrderNumber() + " is ready for a waiter to carry.");
    }

    @Transactional
    public OrderActionResponse deliver(String tenantId, String workerId, String orderId) {
        Order order = lock(tenantId, orderId);
        OrderStatus now = order.getOrderStatus();
        if (now == OrderStatus.DELIVERED) {
            throw new ConflictException(who(order.getDeliveredBy()) + " has already delivered " + order.getOrderNumber() + ".");
        }
        if (now == OrderStatus.PAID || now == OrderStatus.PREPARING) {
            throw new OrderStatusException(order.getOrderNumber() + " is not ready yet.");
        }
        if (now != OrderStatus.READY) {
            throw refused(order, "delivered");
        }
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        order.setDeliveredBy(label(workerId));
        return saved(order, order.getOrderNumber() + " was delivered to the seat.");
    }

    // The older PATCH routes name the step they want. They go through the same rules.
    @Transactional
    public Order moveTo(String tenantId, String workerId, String orderId, String newState) {
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(newState == null ? "" : newState.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new OrderStatusException("orderStatus is not allowed");
        }
        switch (target) {
            case PREPARING -> start(tenantId, workerId, orderId);
            case READY -> ready(tenantId, workerId, orderId);
            case DELIVERED -> deliver(tenantId, workerId, orderId);
            case PAID, CANCELED -> {
                Order order = lock(tenantId, orderId);
                if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                    throw new OrderStatusException("orderStatus is not allowed");
                }
                order.setOrderStatus(target);
                if (target == OrderStatus.PAID) {
                    order.setPaidAt(LocalDateTime.now());
                }
                saved(order, null);
            }
            default -> throw new OrderStatusException("orderStatus is not allowed");
        }
        return orderRepo.findByOrderIdAndTenant_TenantId(orderId, tenantId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }

    private OrderActionResponse saved(Order order, String message) {
        orderRepo.save(order);
        liveUpdates.publish(order.getTenant().getTenantId(),
                order.getDinningSession() == null ? null : order.getDinningSession().getSessionId(),
                "order", order.getOrderId(), order.getOrderStatus().name());
        return new OrderActionResponse(order.getOrderId(), order.getOrderNumber(), order.getOrderStatus().name(), message);
    }

    private Order lock(String tenantId, String orderId) {
        return orderRepo.findForUpdate(orderId, tenantId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }

    private OrderStatusException refused(Order order, String what) {
        return new OrderStatusException(order.getOrderNumber() + " can't be " + what + " now: it is "
                + order.getOrderStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
    }

    private String who(String name) {
        return name == null || name.isBlank() ? "Someone" : name;
    }

    // "Chef Bisi": the role and the first name.
    String label(String workerId) {
        if (workerId == null) return null;
        Worker worker = workerRepo.findById(workerId).orElse(null);
        if (worker == null) return null;
        String role = worker.getRole() == null ? "" : switch (worker.getRole()) {
            case CHEF -> "Chef ";
            case WAITER -> "Waiter ";
            case MANAGER -> "Manager ";
            default -> "";
        };
        String first = worker.getFullName() == null || worker.getFullName().isBlank()
                ? worker.getUsername() : worker.getFullName().trim().split("\\s+")[0];
        return (role + first).trim();
    }
}
