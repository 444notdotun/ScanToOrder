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
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.dtos.request.ConfirmGuestRequest;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SessionSeatView;
import com.scantoorder.scantoorder.dtos.respond.SessionViewResponse;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.SeatOwnershipException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SeatTakenException;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SessionEndedException;
import com.scantoorder.scantoorder.exception.SessionNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Value("${app.holds.enabled:true}")
    private boolean holdsEnabled;

    @Transactional(readOnly = true)
    @Override
    public SessionViewResponse getMySession(CustomerPrincipal caller) {
        return viewFor(requireSeated(caller));
    }

    // All or nothing: one seat that is not free rolls the whole request back.
    @Transactional
    @Override
    public SessionViewResponse holdSeats(CustomerPrincipal caller, List<String> seatIds) {
        if (!holdsEnabled) {
            throw new SeatStatusException("Holding seats is switched off here");
        }
        Set<String> wanted = new LinkedHashSet<>(seatIds);
        if (wanted.isEmpty()) {
            throw new InvalidRequestException("Choose at least one seat to hold");
        }
        Map<String, Seat> locked = lockWithOwnSeat(caller, wanted);
        SessionSeat me = requireSeated(caller);
        String myTableId = me.getSeat().getTableId().getTableId();
        for (String seatId : wanted) {
            Seat seat = locked.get(seatId);
            if (seat == null) {
                throw new SeatNotFoundException("We cannot find one of those seats");
            }
            if (!seat.isActive() || !myTableId.equals(seat.getTableId().getTableId())) {
                throw new SeatStatusException("You can only hold seats at your own table");
            }
            Optional<SessionSeat> current = seatLedger.current(seatId);
            if (current.isPresent()) {
                if (current.get().getStatus() == SessionSeatStatus.HELD && sameSession(current.get(), me)) {
                    throw new SeatStatusException("Seat " + seat.getPosition() + " is already held for your table");
                }
                throw new SeatTakenException("Seat " + seat.getPosition() + " was just taken, so no seats were held. Pick again.");
            }
        }
        for (String seatId : wanted) {
            seatLedger.hold(me.getSession(), locked.get(seatId));
        }
        return viewFor(me);
    }

    @Transactional
    @Override
    public SessionViewResponse releaseHold(CustomerPrincipal caller, String seatId) {
        lockWithOwnSeat(caller, Set.of(seatId));
        SessionSeat me = requireSeated(caller);
        SessionSeat held = seatLedger.current(seatId)
                .filter(membership -> membership.getStatus() == SessionSeatStatus.HELD)
                .orElseThrow(() -> new SeatStatusException("That seat is not being held"));
        if (!sameSession(held, me)) {
            throw new SeatOwnershipException("That seat is held by another group");
        }
        seatLedger.release(held);
        return viewFor(me);
    }

    // Hold first, then confirm: a guest without a phone is only ever seated on a seat this session holds.
    @Transactional
    @Override
    public SessionViewResponse confirmGuest(CustomerPrincipal caller, ConfirmGuestRequest request) {
        if (request.getGuestName() == null || request.getGuestName().isBlank()) {
            throw new InvalidRequestException("Tell us your guest's name");
        }
        lockWithOwnSeat(caller, Set.of(request.getSeatId()));
        SessionSeat me = requireSeated(caller);
        SessionSeat target = seatLedger.current(request.getSeatId())
                .orElseThrow(() -> new SeatStatusException("Hold the seat first, then confirm your guest"));
        if (!sameSession(target, me)) {
            throw new SeatOwnershipException("That seat belongs to another group");
        }
        if (target.getStatus() != SessionSeatStatus.HELD) {
            throw new SeatTakenException("Someone is already sitting in that seat");
        }
        seatLedger.seatGuest(target, request.getGuestName(), null, null, false);
        return viewFor(me);
    }

    // Orders are only for occupied seats: the caller's own, or a guest without a phone in their session.
    // Runs inside the order's transaction and keeps the seat locked until the order is saved.
    @Transactional
    @Override
    public SessionSeat seatToOrderFor(CustomerPrincipal caller, String targetSeatId) {
        if (caller == null || caller.getSeatId() == null) {
            throw new SessionEndedException(SESSION_ENDED);
        }
        String seatId = targetSeatId == null || targetSeatId.isBlank() ? caller.getSeatId() : targetSeatId;
        lockWithOwnSeat(caller, Set.of(seatId));
        SessionSeat me = requireSeated(caller);
        if (seatId.equals(me.getSeat().getSeatId())) {
            return me;
        }
        SessionSeat target = seatLedger.current(seatId)
                .orElseThrow(() -> new SeatStatusException("Nobody is sitting in that seat. If your guest has left, order on your own seat."));
        if (!sameSession(target, me)) {
            throw new SeatOwnershipException("You can only order for seats in your own group");
        }
        if (target.getStatus() != SessionSeatStatus.OCCUPIED) {
            throw new SeatStatusException("That seat is only being held. Order on your own seat until your guest arrives.");
        }
        if (target.isHasDevice()) {
            throw new SeatOwnershipException("That guest has their own phone and orders for themselves");
        }
        return target;
    }

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

    // The caller's own seat is locked with the seats they act on, so leaving or closing cannot run in between.
    private Map<String, Seat> lockWithOwnSeat(CustomerPrincipal caller, Collection<String> seatIds) {
        if (caller == null || caller.getSeatId() == null) {
            throw new SessionEndedException(SESSION_ENDED);
        }
        TreeSet<String> ids = new TreeSet<>(seatIds);
        ids.add(caller.getSeatId());
        Map<String, Seat> locked = new HashMap<>();
        for (Seat seat : seatRepo.findAllForUpdate(ids)) {
            locked.put(seat.getSeatId(), seat);
        }
        return locked;
    }

    private boolean sameSession(SessionSeat one, SessionSeat other) {
        return one.getSession().getSessionId().equals(other.getSession().getSessionId());
    }

    private SessionViewResponse viewFor(SessionSeat me) {
        DinningSession session = me.getSession();
        RestaurantTable table = me.getSeat().getTableId();
        SessionViewResponse response = new SessionViewResponse();
        response.setSessionId(session.getSessionId());
        response.setStatus(session.getSessionStatus().name());
        response.setJoinCode(session.getJoinCode());
        response.setTableId(table.getTableId());
        response.setTableNumber(table.getTableNumber());
        response.setDisplayNumber(table.getDisplayNumber());
        response.setSeatId(me.getSeat().getSeatId());
        response.setHoldsEnabled(holdsEnabled);
        for (SessionSeat membership : activeMembers(session.getSessionId())) {
            boolean mine = membership.getId().equals(me.getId());
            SessionSeatView view = seatView(membership);
            view.setMine(mine);
            // A guest sees their own phone number, never anyone else's.
            view.setGuestPhone(mine ? membership.getGuestPhone() : null);
            response.getSeats().add(view);
        }
        return response;
    }

    private List<SessionSeat> activeMembers(String sessionId) {
        return sessionSeatRepo.findBySessionAndStatusIn(sessionId, SeatLedger.ACTIVE).stream()
                .filter(membership -> !seatLedger.isExpired(membership))
                .toList();
    }

    private SessionSeatView seatView(SessionSeat membership) {
        SessionSeatView view = new SessionSeatView();
        view.setSeatId(membership.getSeat().getSeatId());
        view.setSeatNumber(membership.getSeat().getSeatNumber());
        view.setPosition(membership.getSeat().getPosition());
        view.setStatus(membership.getStatus().name());
        view.setHeldUntil(membership.getHeldUntil());
        if (membership.getStatus() == SessionSeatStatus.OCCUPIED) {
            view.setGuestName(membership.getGuestName());
            view.setHasDevice(membership.isHasDevice());
        }
        return view;
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
