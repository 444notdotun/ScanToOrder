package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventGuest;
import com.scantoorder.scantoorder.data.model.EventSeatingMode;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.EventGuestLimitRequest;
import com.scantoorder.scantoorder.dtos.request.EventGuestRequest;
import com.scantoorder.scantoorder.dtos.respond.EventGuestResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.utils.CodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The planner's guest list. It can never be longer than the number of guests paid for.
 */
@Service
public class EventGuestService {

    public static final String DRAFT_MESSAGE = "Pay for the event first. Until then it is a draft and codes cannot be made.";
    public static final String GUEST_NOT_FOUND = "We cannot find that guest";

    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;

    @Transactional(readOnly = true)
    public List<EventGuestResponse> list(String tenantId, String eventId) {
        event(tenantId, eventId);
        return guestRepo.findByEvent_IdOrderByCreatedAtAsc(eventId).stream().map(EventGuestResponse::of).toList();
    }

    // The event row is locked, so two planners' taps cannot both take the last paid place.
    @Transactional
    public EventGuestResponse add(String tenantId, String eventId, EventGuestRequest request) {
        Event event = eventRepo.findForUpdate(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(EventServiceImpl.NOT_FOUND));
        if (event.getStatus() == EventStatus.ENDED) {
            throw new ConflictException("This event is over, so guests can't be added.");
        }
        if (event.getStatus() == EventStatus.DRAFT) {
            throw new ConflictException(DRAFT_MESSAGE);
        }
        String name = request.getName().trim();
        if (guestRepo.countByEvent_Id(eventId) >= event.getPaidGuests()) {
            throw new ConflictException("You have paid for " + event.getPaidGuests() + " guests and the list is full. Pay for more to add " + name + ".");
        }
        EventGuest guest = new EventGuest();
        guest.setTenant(event.getTenant());
        guest.setEvent(event);
        guest.setName(name);
        guest.setPhone(blankToNull(request.getPhone()));
        guest.setEmail(blankToNull(request.getEmail()));
        guest.setPlateLimit(event.getPlatesPerGuest());
        guest.setCode(freshCode(eventId));
        if (event.getSeatingMode() == EventSeatingMode.ASSIGNED) {
            guest.setAssignedSeat(chooseSeat(event, request));
        } else if (notBlank(request.getTableId()) || notBlank(request.getSeatId())) {
            throw new InvalidRequestException("Seats aren't assigned at this event. Guests sit where they like.");
        }
        return EventGuestResponse.of(guestRepo.save(guest));
    }

    // Only a guest who has never sat down can be taken off the list. That frees a place for someone else.
    @Transactional
    public void remove(String tenantId, String eventId, String guestId) {
        event(tenantId, eventId);
        EventGuest guest = guestRepo.findForUpdate(guestId, eventId).orElseThrow(() -> new ResourceNotFoundException(GUEST_NOT_FOUND));
        if (guest.getSeatedAt() != null) {
            throw new ConflictException(guest.getName() + " has already arrived, so they can't be removed.");
        }
        guestRepo.delete(guest);
    }

    @Transactional
    public EventGuestResponse raiseLimit(String tenantId, String eventId, String guestId, EventGuestLimitRequest request) {
        event(tenantId, eventId);
        EventGuest guest = guestRepo.findForUpdate(guestId, eventId).orElseThrow(() -> new ResourceNotFoundException(GUEST_NOT_FOUND));
        if (request.isUnlimited()) {
            guest.setPlateLimit(null);
        } else {
            Integer limit = request.getLimit();
            if (limit == null) {
                throw new InvalidRequestException("Give a number of plates, or choose unlimited.");
            }
            if (limit < guest.getPlatesUsed()) {
                throw new ConflictException(guest.getName() + " has already had " + guest.getPlatesUsed() + " plates, so the limit can't go below that.");
            }
            if (guest.getPlateLimit() == null) {
                throw new ConflictException(guest.getName() + " already has as many plates as they like.");
            }
            if (limit <= guest.getPlateLimit()) {
                throw new ConflictException("A limit can only be raised. " + guest.getName() + " can have " + guest.getPlateLimit() + " plates now.");
            }
            guest.setPlateLimit(limit);
        }
        return EventGuestResponse.of(guestRepo.save(guest));
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String tenantId, String eventId) {
        event(tenantId, eventId);
        StringBuilder csv = new StringBuilder("Name,Phone,Email,Code,Table,Seat,Plates allowed,Plates used\n");
        for (EventGuest guest : guestRepo.findByEvent_IdOrderByCreatedAtAsc(eventId)) {
            EventGuestResponse row = EventGuestResponse.of(guest);
            csv.append(cell(row.getName())).append(',')
                    .append(cell(row.getPhone())).append(',')
                    .append(cell(row.getEmail())).append(',')
                    .append(cell(row.getCode())).append(',')
                    .append(row.getTableNumber() == null ? "" : row.getTableNumber()).append(',')
                    .append(row.getSeatPosition() == null ? "" : row.getSeatPosition()).append(',')
                    .append(row.getPlateLimit() == null ? "Unlimited" : row.getPlateLimit()).append(',')
                    .append(row.getPlatesUsed()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- seats

    private Seat chooseSeat(Event event, EventGuestRequest request) {
        Set<String> taken = guestRepo.findByEvent_IdOrderByCreatedAtAsc(event.getId()).stream()
                .map(EventGuest::getAssignedSeat).filter(java.util.Objects::nonNull)
                .map(Seat::getSeatId).collect(Collectors.toCollection(HashSet::new));
        List<RestaurantTable> tables = tableRepo.findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(event.getId());
        if (notBlank(request.getSeatId())) {
            Seat seat = seatRepo.findBySeatIdAndTenant_TenantId(request.getSeatId(), event.getTenant().getTenantId())
                    .filter(Seat::isActive)
                    .filter(found -> found.getTableId() != null && tables.stream().anyMatch(t -> t.getTableId().equals(found.getTableId().getTableId())))
                    .orElseThrow(() -> new ResourceNotFoundException("We cannot find that seat at this event"));
            if (notBlank(request.getTableId()) && !seat.getTableId().getTableId().equals(request.getTableId())) {
                throw new InvalidRequestException("That seat isn't at that table.");
            }
            if (taken.contains(seat.getSeatId())) {
                throw new ConflictException("That seat already belongs to another guest. Choose another.");
            }
            return seat;
        }
        if (notBlank(request.getTableId())) {
            RestaurantTable table = tables.stream().filter(t -> t.getTableId().equals(request.getTableId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("We cannot find that table at this event"));
            return firstFree(List.of(table), taken, "That table is full. Choose another table.");
        }
        return firstFree(tables, taken, "Every seat is taken. Add a table, then add this guest again.");
    }

    // The next free seat in table order, then seat order.
    private Seat firstFree(List<RestaurantTable> tables, Set<String> taken, String none) {
        for (RestaurantTable table : tables) {
            for (Seat seat : seatRepo.findByTableIdOrderByPositionAsc(table)) {
                if (seat.isActive() && !taken.contains(seat.getSeatId())) {
                    return seat;
                }
            }
        }
        throw new ConflictException(none);
    }

    // ---- helpers

    private String freshCode(String eventId) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = CodeGenerator.generateJoinCode();
            if (!guestRepo.existsByEvent_IdAndCode(eventId, code)) {
                return code;
            }
        }
        throw new ConflictException("We could not make a new code. Try again.");
    }

    private Event event(String tenantId, String eventId) {
        return eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(EventServiceImpl.NOT_FOUND));
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // A cell that starts like a formula is made harmless, and commas and quotes are escaped.
    private String cell(String value) {
        if (value == null) return "";
        String processed = value;
        if (!processed.isEmpty() && "=+-@\t\r".indexOf(processed.charAt(0)) >= 0) {
            processed = "'" + processed;
        }
        if (processed.contains(",") || processed.contains("\"") || processed.contains("\n") || processed.contains("\r")) {
            processed = "\"" + processed.replace("\"", "\"\"") + "\"";
        }
        return processed;
    }
}
