package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The only place a seat changes state. Callers hold the seat's row lock first.
 */
@Component
public class SeatLedger {

    public static final List<SessionSeatStatus> ACTIVE = List.of(SessionSeatStatus.HELD, SessionSeatStatus.OCCUPIED);
    public static final int HOLD_MINUTES = 10;

    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private LiveUpdates liveUpdates;

    /**
     * Who has the seat right now. A hold that has run out is released here, so it counts as vacant.
     */
    public Optional<SessionSeat> current(String seatId) {
        Optional<SessionSeat> found = sessionSeatRepo.findBySeatAndStatusIn(seatId, ACTIVE);
        if (found.isPresent() && isExpired(found.get())) {
            release(found.get());
            return Optional.empty();
        }
        return found;
    }

    public boolean isExpired(SessionSeat membership) {
        return membership.getStatus() == SessionSeatStatus.HELD
                && membership.getHeldUntil() != null
                && !membership.getHeldUntil().isAfter(LocalDateTime.now());
    }

    public SessionSeat occupy(DinningSession session, Seat seat, String name, String phone, String email) {
        SessionSeat membership = new SessionSeat();
        membership.setSession(session);
        membership.setSeat(seat);
        membership.setTenant(seat.getTenant());
        seatGuest(membership, name, phone, email, true);
        return membership;
    }

    public SessionSeat hold(DinningSession session, Seat seat) {
        SessionSeat membership = new SessionSeat();
        membership.setSession(session);
        membership.setSeat(seat);
        membership.setTenant(seat.getTenant());
        membership.setStatus(SessionSeatStatus.HELD);
        membership.setHeldAt(LocalDateTime.now());
        membership.setHeldUntil(LocalDateTime.now().plusMinutes(HOLD_MINUTES));
        sessionSeatRepo.save(membership);
        seat.setStatus(SeatStatus.HELD);
        seatRepo.save(seat);
        seatChanged(membership, "HELD");
        return membership;
    }

    public void seatGuest(SessionSeat membership, String name, String phone, String email, boolean hasDevice) {
        membership.setStatus(SessionSeatStatus.OCCUPIED);
        membership.setGuestName(name == null ? null : name.trim());
        membership.setGuestPhone(phone);
        membership.setGuestEmail(email == null || email.isBlank() ? null : email.trim());
        membership.setHasDevice(hasDevice);
        membership.setHeldUntil(null);
        membership.setClaimedAt(LocalDateTime.now());
        sessionSeatRepo.save(membership);
        Seat seat = membership.getSeat();
        seat.setStatus(SeatStatus.OCCUPIED);
        seatRepo.save(seat);
        seatChanged(membership, "OCCUPIED");
    }

    public void release(SessionSeat membership) {
        membership.setStatus(SessionSeatStatus.RELEASED);
        membership.setHeldUntil(null);
        membership.setReleasedAt(LocalDateTime.now());
        // Flushed now: the one-active-record-per-seat index must see this before a new record is inserted.
        sessionSeatRepo.saveAndFlush(membership);
        Seat seat = membership.getSeat();
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        seatChanged(membership, "VACANT");
    }

    // Staff screens and the guests of this session are told a seat changed.
    private void seatChanged(SessionSeat membership, String state) {
        liveUpdates.publish(membership.getTenant().getTenantId(),
                membership.getSession() == null ? null : membership.getSession().getSessionId(),
                "seat", membership.getSeat().getSeatId(), state);
    }

    public int cancelUnpaidOrders(SessionSeat membership) {
        List<Order> unpaid = orderRepo.findBySessionSeat_IdAndOrderStatus(membership.getId(), OrderStatus.PENDING_PAYMENT);
        for (Order order : unpaid) {
            order.setOrderStatus(OrderStatus.CANCELED);
        }
        orderRepo.saveAll(unpaid);
        return unpaid.size();
    }
}
