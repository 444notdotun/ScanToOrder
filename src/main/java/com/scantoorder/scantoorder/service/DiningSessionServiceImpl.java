package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SessionEndedException;
import com.scantoorder.scantoorder.exception.SessionNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Service
public class DiningSessionServiceImpl implements DiningSessionService {

    public static final String SESSION_ENDED = "Your session has ended";
    private static final List<OrderStatus> PAID_UNDELIVERED = List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY);

    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private SeatLedger seatLedger;

    @Transactional
    @Override
    public CloseSessionResponse closeSession(String sessionId) {
        DinningSession session = dinningSessionRepo.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("We cannot find that session"));
        if (session.getSessionStatus() == DinningSessionStatus.CLOSED) {
            return new CloseSessionResponse("Session is already closed", sessionId, DinningSessionStatus.CLOSED.name());
        }
        lockSeats(sessionId, List.of());
        long paidUndelivered = countPaidUndelivered(sessionId);
        finishSession(session);
        return new CloseSessionResponse("Session closed successfully", sessionId, DinningSessionStatus.CLOSED.name(), paidUndelivered);
    }

    @Transactional
    @Override
    public ReleaseSeatResponse freeSeat(String seatId) {
        String sessionId = sessionSeatRepo.findSessionIdBySeatAndStatusIn(seatId, SeatLedger.ACTIVE).orElse(null);
        List<Seat> locked = lockSeats(sessionId, List.of(seatId));
        Seat seat = locked.stream().filter(s -> s.getSeatId().equals(seatId)).findFirst()
                .orElseThrow(() -> new SeatNotFoundException("We cannot find that seat"));

        Optional<SessionSeat> current = seatLedger.current(seatId);
        if (current.isEmpty()) {
            if (seat.getStatus() != SeatStatus.VACANT) {
                seat.setStatus(SeatStatus.VACANT);
                seatRepo.save(seat);
            }
            return new ReleaseSeatResponse("Seat is already free", seat.getSeatId(), seat.getSeatNumber(), SeatStatus.VACANT.name());
        }
        SessionSeat membership = current.get();
        boolean wasOccupied = membership.getStatus() == SessionSeatStatus.OCCUPIED;
        if (wasOccupied) {
            seatLedger.cancelUnpaidOrders(membership);
        }
        seatLedger.release(membership);
        if (wasOccupied) {
            closeIfEmpty(membership.getSession());
        }
        return new ReleaseSeatResponse("Seat released successfully", seat.getSeatId(), seat.getSeatNumber(), SeatStatus.VACANT.name());
    }

    @Override
    public long getActiveSessionsCount() {
        return dinningSessionRepo.countBySessionStatus(DinningSessionStatus.ACTIVE);
    }

    // A guest sees receipts for their own seat and for guests without a phone at their table, not for other phones.
    @Transactional(readOnly = true)
    @Override
    public List<String> getSessionPaymentReferences(CustomerPrincipal caller) {
        SessionSeat me = requireSeated(caller);
        List<Payment> payments = paymentRepo.findByOrderDinningSessionSessionIdAndStatus(
                me.getSession().getSessionId(), PaymentStatus.SUCCESSFUL);
        return payments.stream()
                .filter(payment -> {
                    SessionSeat owner = payment.getOrder().getSessionSeat();
                    return owner == null || owner.getId().equals(me.getId()) || !owner.isHasDevice();
                })
                .map(Payment::getReference)
                .toList();
    }

    private SessionSeat requireSeated(CustomerPrincipal caller) {
        if (caller == null || caller.getSessionSeatId() == null) {
            throw new SessionEndedException(SESSION_ENDED);
        }
        SessionSeat me = sessionSeatRepo.findById(caller.getSessionSeatId())
                .orElseThrow(() -> new SessionEndedException(SESSION_ENDED));
        if (me.getStatus() != SessionSeatStatus.OCCUPIED
                || me.getSession().getSessionStatus() != DinningSessionStatus.ACTIVE) {
            throw new SessionEndedException(SESSION_ENDED);
        }
        return me;
    }

    // Locks every held and occupied seat of the session plus any extra seats, in one id-ordered query.
    private List<Seat> lockSeats(String sessionId, Collection<String> extraSeatIds) {
        TreeSet<String> ids = new TreeSet<>(extraSeatIds);
        if (sessionId != null) {
            ids.addAll(sessionSeatRepo.findSeatIdsBySessionAndStatusIn(sessionId, SeatLedger.ACTIVE));
        }
        return ids.isEmpty() ? List.of() : seatRepo.findAllForUpdate(ids);
    }

    private boolean closeIfEmpty(DinningSession session) {
        if (sessionSeatRepo.countBySessionAndStatus(session.getSessionId(), SessionSeatStatus.OCCUPIED) > 0) {
            return false;
        }
        finishSession(session);
        return true;
    }

    // Frees every held and occupied seat, cancels what was never paid for, and closes the session.
    private void finishSession(DinningSession session) {
        for (SessionSeat membership : sessionSeatRepo.findBySessionAndStatusIn(session.getSessionId(), SeatLedger.ACTIVE)) {
            seatLedger.release(membership);
        }
        List<Order> unpaid = orderRepo.findByDinningSessionSessionIdAndOrderStatus(session.getSessionId(), OrderStatus.PENDING_PAYMENT);
        for (Order order : unpaid) {
            order.setOrderStatus(OrderStatus.CANCELED);
        }
        orderRepo.saveAll(unpaid);
        session.setSessionStatus(DinningSessionStatus.CLOSED);
        session.setCompletedAt(LocalDateTime.now().toString());
        dinningSessionRepo.save(session);
    }

    private long countPaidUndelivered(String sessionId) {
        return orderRepo.countByDinningSessionSessionIdAndOrderStatusIn(sessionId, PAID_UNDELIVERED);
    }
}
