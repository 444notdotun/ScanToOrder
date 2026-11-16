package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.EventPlate;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.RefundReason;
import com.scantoorder.scantoorder.data.model.UnavailableReason;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.EventPlateRepo;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.SwapRequest;
import com.scantoorder.scantoorder.dtos.respond.OrderActionResponse;
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
import java.util.Comparator;
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
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private LiveUpdates liveUpdates;
    @Autowired
    private EventPlateRepo plateRepo;
    @Autowired
    private EventGuestRepo guestRepo;

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
        if (order.getEvent() != null) {
            // A plate nobody could make is not a plate the guest has had.
            countPlate(order, -1);
        }
        if (reason == UnavailableReason.RAN_OUT) {
            for (OrderItem line : activeLines(orderId)) {
                EventPlate plate = line.getPlate();
                if (plate != null && plate.isAvailable()) {
                    plate.setAvailable(false);
                    plateRepo.save(plate);
                }
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
        if (order.getEvent() != null) {
            return swapPlate(order, request);
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
        // A new ticket starts again from the first step.
        order.setPreparingAt(null);
        order.setPreparingBy(null);
        order.setReadyAt(null);
        order.setReadyBy(null);
        orderRepo.save(order);
        return action(order, "The kitchen has a new ticket for this order.");
    }

    @Transactional
    @Override
    public OrderActionResponse requestRefund(String tenantId, String orderId) {
        Order order = lock(tenantId, orderId);
        if (order.getEvent() != null) {
            throw new OrderStatusException("Nothing was paid for this order, so there is nothing to refund. Swap it for another plate, or the guest can order again.");
        }
        if (order.getOrderStatus() != OrderStatus.UNAVAILABLE) {
            throw new OrderStatusException("A refund can only be asked for on an order the kitchen could not make.");
        }
        // The payment is locked so a webhook arriving now cannot start a second refund for it.
        Payment payment = paymentRepo.findByOrder_OrderIdAndStatusIn(orderId, List.of(PaymentStatus.SUCCESSFUL)).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt))
                .map(found -> paymentRepo.findForUpdateByReference(found.getReference()).orElse(found))
                .filter(found -> refundRepo.findByPayment_Id(found.getId()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new OrderStatusException("There is no payment on this order to refund."));
        refundService.createDue(payment, RefundReason.KITCHEN_UNAVAILABLE);
        order.setOrderStatus(OrderStatus.REFUND_DUE);
        orderRepo.save(order);
        return action(order, "The restaurant owes the guest a refund. The manager will settle it.");
    }

    // An event order is exactly one plate, so a swap names one plate and no price is involved.
    private OrderActionResponse swapPlate(Order order, SwapRequest request) {
        if (request.getItems().size() != 1 || request.getItems().get(0).getQuantity() != 1) {
            throw new InvalidRequestException("An event order is one plate. Choose one.");
        }
        String wanted = request.getItems().get(0).getItemName();
        EventPlate plate = plateRepo.findByEvent_IdAndNameIgnoreCase(order.getEvent().getId(), wanted == null ? "" : wanted.trim())
                .orElseThrow(() -> new ConflictException("We cannot find " + wanted + " on this event's plates."));
        if (!plate.isAvailable()) {
            throw new ConflictException(plate.getName() + " has run out. Choose another plate.");
        }
        String was = "";
        for (OrderItem line : activeLines(order.getOrderId())) {
            was = line.getPlate() == null ? was : line.getPlate().getName();
            line.setSwappedOut(true);
            orderItemRepo.save(line);
        }
        OrderItem line = new OrderItem();
        line.setTenant(order.getTenant());
        line.setOrder(order);
        line.setPlate(plate);
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.ZERO);
        line.setTotalPrice(BigDecimal.ZERO);
        orderItemRepo.save(line);
        String note = "Swapped at the table: was " + was + ", now " + plate.getName() + ".";
        order.setNote(order.getNote() == null ? note : order.getNote() + " " + note);
        order.setOrderStatus(OrderStatus.PAID);
        order.setSwappedAt(LocalDateTime.now());
        order.setPreparingAt(null);
        order.setPreparingBy(null);
        order.setReadyAt(null);
        order.setReadyBy(null);
        orderRepo.save(order);
        // The guest has this plate now, so it counts again.
        countPlate(order, 1);
        return action(order, "The kitchen has a new ticket for this order.");
    }

    private void countPlate(Order order, int change) {
        if (order.getEventGuest() == null) return;
        guestRepo.findForUpdate(order.getEventGuest().getId(), order.getEvent().getId()).ifPresent(guest -> {
            guest.setPlatesUsed(Math.max(0, guest.getPlatesUsed() + change));
            guestRepo.save(guest);
        });
    }

    private Order lock(String tenantId, String orderId) {
        return orderRepo.findForUpdate(orderId, tenantId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }

    private List<OrderItem> activeLines(String orderId) {
        return orderItemRepo.findByOrder_OrderId(orderId).stream().filter(line -> !line.isSwappedOut()).toList();
    }

    private OrderActionResponse action(Order order, String message) {
        liveUpdates.publish(order.getTenant().getTenantId(),
                order.getDinningSession() == null ? null : order.getDinningSession().getSessionId(),
                "order", order.getOrderId(), order.getOrderStatus().name());
        return new OrderActionResponse(order.getOrderId(), order.getOrderNumber(), order.getOrderStatus().name(), message);
    }
}
