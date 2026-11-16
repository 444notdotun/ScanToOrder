package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventGuest;
import com.scantoorder.scantoorder.data.model.EventSeatingMode;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.respond.EventTablePreviewResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SeatTakenException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * At the event, a guest scans their table and types their code. The code is their seat and their payment.
 * Every seat change goes through the ledger with the seat row locked, as in a restaurant.
 */
@Service
public class EventSeatingService {

    public static final String UNKNOWN_CODE = "We can't find that code. Check the letters, or ask the host.";
    public static final String EVENT_OVER = "This event is over. Thank you for coming.";
    public static final String TABLE_FULL = "This table is full. Try another table.";
    private static final String TABLE_NOT_AVAILABLE = "This table is not available";

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private com.scantoorder.scantoorder.data.repository.EventRepo eventRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo sessionRepo;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private SeatLedger seatLedger;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private DiningSessionService diningSessionService;

    // The table's QR link names the business, the event and the table. An unknown one answers like any missing table.
    @Transactional(readOnly = true)
    public EventTablePreviewResponse preview(String slug, String eventCode, int displayNumber) {
        Tenant tenant = tenantRepo.findBySlug(slug == null ? "" : slug.toLowerCase())
                .filter(found -> found.getType() == TenantType.EVENT_PLANNER)
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        Event event = eventRepo.findByTenant_TenantIdAndEventCode(tenant.getTenantId(), eventCode == null ? "" : eventCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        tableRepo.findByEvent_IdAndDisplayNumber(event.getId(), displayNumber)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        LocalDateTime now = LocalDateTime.now();
        boolean over = event.getStatus() == EventStatus.ENDED || !now.isBefore(event.getEndsAt());
        boolean open = !over && !now.isBefore(event.getStartsAt());
        return new EventTablePreviewResponse(event.getName(), event.getVenue(), displayNumber, event.getStartsAt(), event.getEndsAt(), open, over);
    }

    @Transactional
    public SeatClaimedResponse redeem(String slug, String eventCode, int displayNumber, String rawCode, CustomerPrincipal caller) {
        // An unknown business, a restaurant and an unknown table are all answered alike.
        Tenant tenant = tenantRepo.findBySlug(slug == null ? "" : slug.toLowerCase())
                .filter(found -> found.getType() == TenantType.EVENT_PLANNER)
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        Event event = eventRepo.findByTenant_TenantIdAndEventCode(tenant.getTenantId(), eventCode == null ? "" : eventCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        RestaurantTable table = tableRepo.findByEvent_IdAndDisplayNumber(event.getId(), displayNumber)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));

        LocalDateTime now = LocalDateTime.now();
        if (event.getStatus() == EventStatus.ENDED || !now.isBefore(event.getEndsAt())) {
            throw new ConflictException(EVENT_OVER);
        }
        if (now.isBefore(event.getStartsAt())) {
            throw new ConflictException("Doors open at " + event.getStartsAt().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
                    + ". Come back then and your code will work.");
        }

        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        // A code belongs to one event: another event's code is simply not on this list.
        EventGuest guest = guestRepo.findForUpdateByCode(event.getId(), code)
                .orElseThrow(() -> new ResourceNotFoundException(UNKNOWN_CODE));

        SessionSeat existing = guest.getSessionSeat();
        if (existing != null && existing.getStatus() == SessionSeatStatus.OCCUPIED
                && existing.getSession().getSessionStatus() == DinningSessionStatus.ACTIVE) {
            // The same phone coming back keeps its seat and its token; any other phone is told where the code is in use.
            if (caller != null && existing.getId().equals(caller.getSessionSeatId())) {
                return seated("You are already seated", existing, guest, false);
            }
            throw new SeatTakenException("It is in use at " + label(existing.getSeat())
                    + " on another phone. Find an usher and they will move it to this one.");
        }
        refuseIfSeatedAsSomeoneElse(caller);

        Seat seat = event.getSeatingMode() == EventSeatingMode.ASSIGNED
                ? assignedSeat(guest, table)
                : freeSeat(table);

        DinningSession session = new DinningSession();
        session.setTableId(table);
        session.setTenant(tenant);
        session = sessionRepo.save(session);
        SessionSeat membership = seatLedger.occupy(session, seat, guest.getName(), guest.getPhone(), guest.getEmail());
        guest.setSessionSeat(membership);
        if (guest.getSeatedAt() == null) {
            guest.setSeatedAt(now);
        }
        guestRepo.save(guest);
        return seated("Welcome, " + guest.getName(), membership, guest, true);
    }

    // The usher moves a code to a new phone by freeing its seat. Plates already used are kept.
    @Transactional
    public ReleaseSeatResponse release(String tenantId, String eventId, String guestId) {
        EventGuest guest = guestRepo.findByIdAndEvent_Id(guestId, eventId)
                .filter(found -> found.getTenant().getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException(EventGuestService.GUEST_NOT_FOUND));
        SessionSeat membership = guest.getSessionSeat();
        if (membership == null || membership.getStatus() != SessionSeatStatus.OCCUPIED) {
            return new ReleaseSeatResponse(guest.getName() + " is not seated, so their code already works on a new phone.", null, null, "VACANT");
        }
        return diningSessionService.freeSeat(tenantId, membership.getSeat().getSeatId());
    }

    // ---- seats

    private Seat assignedSeat(EventGuest guest, RestaurantTable here) {
        Seat mine = guest.getAssignedSeat();
        if (mine == null) {
            throw new ConflictException("You don't have a seat yet. Ask the host.");
        }
        if (!Objects.equals(mine.getTableId().getTableId(), here.getTableId())) {
            throw new SeatStatusException("Your seat is at " + label(mine) + ".");
        }
        Seat seat = seatRepo.findForUpdate(mine.getSeatId()).orElseThrow(() -> new ResourceNotFoundException("We cannot find that seat"));
        if (seatLedger.current(seat.getSeatId()).isPresent()) {
            throw new SeatTakenException("Someone is sitting in your seat, " + label(seat) + ". Find an usher.");
        }
        return seat;
    }

    private Seat freeSeat(RestaurantTable table) {
        for (Seat candidate : seatRepo.findByTableIdOrderByPositionAsc(table)) {
            if (!candidate.isActive()) continue;
            Seat seat = seatRepo.findForUpdate(candidate.getSeatId()).orElse(null);
            if (seat != null && seatLedger.current(seat.getSeatId()).isEmpty()) {
                return seat;
            }
        }
        throw new SeatTakenException(TABLE_FULL);
    }

    // A guest who already sits at the event under another code is not seated a second time.
    private void refuseIfSeatedAsSomeoneElse(CustomerPrincipal caller) {
        if (caller == null || caller.getSessionSeatId() == null) return;
        SessionSeat mine = sessionSeatRepo.findById(caller.getSessionSeatId()).orElse(null);
        if (mine != null && mine.getStatus() == SessionSeatStatus.OCCUPIED
                && mine.getSession().getSessionStatus() == DinningSessionStatus.ACTIVE) {
            throw new SeatStatusException("You already have a seat at " + label(mine.getSeat()) + ". Leave it first, then try again.");
        }
    }

    private String label(Seat seat) {
        return "Table " + seat.getTableId().getDisplayNumber() + ", Seat " + seat.getPosition();
    }

    private SeatClaimedResponse seated(String message, SessionSeat membership, EventGuest guest, boolean issueToken) {
        Seat seat = membership.getSeat();
        RestaurantTable table = seat.getTableId();
        DinningSession session = membership.getSession();
        SeatClaimedResponse response = new SeatClaimedResponse();
        response.setMessage(message);
        response.setSessionId(session.getSessionId());
        response.setSeatId(seat.getSeatId());
        response.setSeatNumber(seat.getSeatNumber());
        response.setPosition(seat.getPosition());
        response.setTableNumber(table.getTableNumber());
        response.setDisplayNumber(table.getDisplayNumber());
        if (issueToken) {
            response.setToken(jwtService.generateCustomerToken(session.getSessionId(), seat.getSeatId(), table.getTableId(),
                    membership.getId(), membership.getTenant().getTenantId()));
        }
        return response;
    }
}
