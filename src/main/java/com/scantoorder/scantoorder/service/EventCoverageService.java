package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.EventGuest;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.dtos.respond.EventCoverageResponse;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Has everyone eaten? Worked out from the guests' orders, never stored.
 */
@Service
public class EventCoverageService {

    private static final Set<OrderStatus> WAITING = EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.UNAVAILABLE);

    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private OrderRepo orderRepo;

    @Transactional(readOnly = true)
    public EventCoverageResponse coverage(String tenantId, String eventId) {
        eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(EventServiceImpl.NOT_FOUND));
        Map<String, List<Order>> ordersByGuest = new HashMap<>();
        for (Order order : orderRepo.findByEvent_Id(eventId)) {
            if (order.getEventGuest() != null) {
                ordersByGuest.computeIfAbsent(order.getEventGuest().getId(), key -> new java.util.ArrayList<>()).add(order);
            }
        }
        EventCoverageResponse response = new EventCoverageResponse();
        for (EventGuest guest : guestRepo.findByEvent_IdOrderByCreatedAtAsc(eventId)) {
            List<Order> orders = ordersByGuest.getOrDefault(guest.getId(), List.of());
            String state;
            if (orders.stream().anyMatch(order -> order.getOrderStatus() == OrderStatus.DELIVERED)) {
                state = "EATEN";
                response.setEaten(response.getEaten() + 1);
            } else if (orders.stream().anyMatch(order -> WAITING.contains(order.getOrderStatus()))) {
                state = "WAITING_FOR_PLATE";
                response.setWaitingForPlate(response.getWaitingForPlate() + 1);
            } else if (guest.getSeatedAt() != null) {
                state = "SEATED_NOT_ORDERED";
                response.setSeatedNotOrdered(response.getSeatedNotOrdered() + 1);
            } else {
                state = "NOT_ARRIVED";
                response.setNotArrived(response.getNotArrived() + 1);
            }
            Seat seat = placeOf(guest);
            response.getGuests().add(new EventCoverageResponse.Guest(guest.getId(), guest.getName(), state,
                    seat == null ? null : seat.getTableId().getDisplayNumber(), seat == null ? null : seat.getPosition(),
                    guest.getPlatesUsed(), guest.getPlateLimit()));
        }
        response.setOnList(response.getGuests().size());
        return response;
    }

    // Where they sit now, or the seat they were given.
    private Seat placeOf(EventGuest guest) {
        SessionSeat membership = guest.getSessionSeat();
        if (membership != null && membership.getStatus() == SessionSeatStatus.OCCUPIED) {
            return membership.getSeat();
        }
        return guest.getAssignedSeat();
    }
}
