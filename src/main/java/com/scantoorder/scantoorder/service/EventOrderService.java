package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventGuest;
import com.scantoorder.scantoorder.data.model.EventPlate;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.EventPlateRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.dtos.respond.EventOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.EventPlateResponse;
import com.scantoorder.scantoorder.dtos.respond.MyEventResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An event guest taps a plate and it goes straight to the kitchen as paid. Nothing is charged and no payment is made.
 */
@Service
public class EventOrderService {

    // A plate is on its way until it is delivered. One that the kitchen gave up on is not.
    static final List<OrderStatus> ON_ITS_WAY = List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY);

    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private EventPlateRepo plateRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private LiveUpdates liveUpdates;
    @Autowired
    private OrderItemRepo orderItemRepo;

    @Transactional(readOnly = true)
    public MyEventResponse myEvent(CustomerPrincipal caller) {
        EventGuest guest = myGuest(caller);
        Event event = guest.getEvent();
        MyEventResponse response = new MyEventResponse();
        response.setEventName(event.getName());
        response.setVenue(event.getVenue());
        response.setEndsAt(event.getEndsAt());
        response.setGuestName(guest.getName());
        response.setPlatesUsed(guest.getPlatesUsed());
        response.setPlateLimit(guest.getPlateLimit());
        response.setPlatePending(plateOnItsWay(guest));
        for (EventPlate plate : plateRepo.findByEvent_IdOrderByNameAsc(event.getId())) {
            if (plate.isAvailable()) {
                response.getPlates().add(EventPlateResponse.of(plate));
            }
        }
        return response;
    }

    // The guest's row is locked, so two quick taps cannot both pass the one-at-a-time and the limit checks.
    @Transactional
    public EventOrderResponse order(CustomerPrincipal caller, String plateId) {
        SessionSeat me = diningSessionService.seatToOrderFor(caller, null);
        EventGuest guest = guestRepo.findForUpdateBySessionSeat(me.getId())
                .orElseThrow(() -> new ResourceNotFoundException("This seat isn't at an event"));
        Event event = guest.getEvent();
        if (event.getStatus() == EventStatus.ENDED || !LocalDateTime.now().isBefore(event.getEndsAt())) {
            throw new ConflictException(EventSeatingService.EVENT_OVER);
        }
        EventPlate plate = plateRepo.findByIdAndEvent_Id(plateId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException(EventPlateService.PLATE_NOT_FOUND));
        if (!plate.isAvailable()) {
            throw new ConflictException(plate.getName() + " has run out. Choose another plate.");
        }
        String onItsWay = plateOnItsWay(guest);
        if (onItsWay != null) {
            throw new ConflictException("One plate at a time. Your " + onItsWay + " is on its way. Order again once it arrives.");
        }
        if (guest.getPlateLimit() != null && guest.getPlatesUsed() >= guest.getPlateLimit()) {
            throw new ConflictException("You've had all " + guest.getPlateLimit() + " of your plates. The host can add more for you.");
        }
        // Ordering again covers a plate the kitchen could not make: the old ticket is closed.
        for (Order stuck : orderRepo.findByEventGuest_IdAndOrderStatusIn(guest.getId(), List.of(OrderStatus.UNAVAILABLE))) {
            stuck.setOrderStatus(OrderStatus.CANCELED);
            orderRepo.save(stuck);
        }

        Order order = new Order();
        order.setTenant(event.getTenant());
        order.setSeat(me.getSeat());
        order.setTable(me.getSeat().getTableId());
        order.setDinningSession(me.getSession());
        order.setSessionSeat(me);
        order.setEvent(event);
        order.setEventGuest(guest);
        order.setTotalAmount(BigDecimal.ZERO);
        order = orderRepo.save(order);
        // Straight to the kitchen as paid: there is nothing to pay.
        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order = orderRepo.save(order);
        liveUpdates.publish(event.getTenant().getTenantId(), me.getSession().getSessionId(), "order", order.getOrderId(), "PAID");
        OrderItem line = new OrderItem();
        line.setTenant(event.getTenant());
        line.setOrder(order);
        line.setPlate(plate);
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.ZERO);
        line.setTotalPrice(BigDecimal.ZERO);
        orderItemRepo.save(line);

        guest.setPlatesUsed(guest.getPlatesUsed() + 1);
        guestRepo.save(guest);
        return new EventOrderResponse(order.getOrderId(), order.getOrderNumber(), plate.getName(), order.getOrderStatus().name(),
                guest.getPlatesUsed(), guest.getPlateLimit(), "Nothing to pay. Your plate has gone straight to the kitchen.");
    }

    // The name of the plate this guest is still waiting for, or null.
    private String plateOnItsWay(EventGuest guest) {
        for (Order order : orderRepo.findByEventGuest_IdAndOrderStatusIn(guest.getId(), ON_ITS_WAY)) {
            for (OrderItem line : orderItemRepo.findByOrder_OrderId(order.getOrderId())) {
                if (!line.isSwappedOut() && line.getPlate() != null) {
                    return line.getPlate().getName();
                }
            }
        }
        return null;
    }

    private EventGuest myGuest(CustomerPrincipal caller) {
        if (caller == null || caller.getSessionSeatId() == null) {
            throw new ResourceNotFoundException("This seat isn't at an event");
        }
        return guestRepo.findBySessionSeat_Id(caller.getSessionSeatId()).orElseThrow(() -> new ResourceNotFoundException("This seat isn't at an event"));
    }
}
