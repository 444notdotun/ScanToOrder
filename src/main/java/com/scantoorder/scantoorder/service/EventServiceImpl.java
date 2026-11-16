package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CodePrefix;
import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventPayment;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.EventPaymentRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.EventRequest;
import com.scantoorder.scantoorder.dtos.respond.EventPriceResponse;
import com.scantoorder.scantoorder.dtos.respond.EventResponse;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.EventService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.utils.CodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class EventServiceImpl implements EventService {

    public static final String NOT_FOUND = "We cannot find that event";
    public static final String ONLY_PLANNERS = "Only event planners can run events.";

    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private EventPaymentRepo paymentRepo;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private EventPricing pricing;
    @Autowired
    private PaystackClient paystackClient;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Transactional
    @Override
    public EventResponse create(String tenantId, EventRequest request) {
        Tenant tenant = tenantRepo.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find your business"));
        if (tenant.getType() != TenantType.EVENT_PLANNER) {
            throw new ConflictException(ONLY_PLANNERS);
        }
        checkTimes(request);
        if (!request.getEndsAt().isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("The event has to end in the future.");
        }
        Event event = new Event();
        event.setTenant(tenant);
        event.setStatus(EventStatus.DRAFT);
        apply(event, request);
        return respond(eventRepo.save(event));
    }

    @Transactional
    @Override
    public EventResponse update(String tenantId, String eventId, EventRequest request) {
        Event event = eventRepo.findForUpdate(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (event.getStatus() == EventStatus.ENDED) {
            throw new ConflictException("This event is over, so it can't be changed.");
        }
        checkTimes(request);
        // Seats are given out by the mode, so it cannot change once there are guests on the list.
        if (request.getSeatingMode() != event.getSeatingMode() && guestRepo.countByEvent_Id(eventId) > 0) {
            throw new ConflictException("You already have guests on the list, so the seating can't change now.");
        }
        apply(event, request);
        return respond(eventRepo.save(event));
    }

    @Transactional(readOnly = true)
    @Override
    public EventResponse get(String tenantId, String eventId) {
        return respond(eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventResponse> list(String tenantId) {
        return eventRepo.findByTenant_TenantIdOrderByStartsAtDesc(tenantId).stream().map(this::respond).toList();
    }

    @Transactional
    @Override
    public EventResponse end(String tenantId, String eventId) {
        Event event = eventRepo.findForUpdate(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        finish(event);
        return respond(event);
    }

    @Transactional(readOnly = true)
    @Override
    public EventPriceResponse price(String tenantId, String eventId, int guests) {
        eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        requireGuests(guests);
        return pricing.price(guests);
    }

    // Paystack is asked first and a payment is saved only once it has accepted. No transaction is open meanwhile.
    @Override
    public InitializePaymentResponse startPayment(String tenantId, String eventId, int guests) {
        Event event = eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (event.getStatus() == EventStatus.ENDED) {
            throw new ConflictException("This event is over, so there is nothing to pay for.");
        }
        requireGuests(guests);
        long amountKobo = pricing.amountKobo(guests);
        String reference = CodeGenerator.generate(CodePrefix.EVENT);
        // The planner's own email: the whole fee is Plately's, so Paystack's receipt goes to the planner.
        PaystackInitResponseData paystack = paystackClient.initialize(event.getTenant().getEmail(), amountKobo, reference, null);
        transactionTemplate.executeWithoutResult(status -> {
            EventPayment payment = new EventPayment();
            payment.setTenant(event.getTenant());
            payment.setEvent(event);
            payment.setGuests(guests);
            payment.setAmountKobo(amountKobo);
            payment.setReference(reference);
            paymentRepo.save(payment);
        });
        return new InitializePaymentResponse(paystack.getAuthorizationUrl(), reference);
    }

    @Override
    public int endFinishedEvents() {
        List<String> ids = transactionTemplate.execute(status -> eventRepo
                .findByStatusInAndEndsAtBefore(List.of(EventStatus.DRAFT, EventStatus.ACTIVE), LocalDateTime.now())
                .stream().map(Event::getId).toList());
        int ended = 0;
        for (String id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Event event = eventRepo.findById(id).orElse(null);
                    if (event != null && event.getStatus() != EventStatus.ENDED) {
                        finish(event);
                    }
                });
                ended++;
            } catch (RuntimeException e) {
                log.error("Could not end event {}", id, e);
            }
        }
        return ended;
    }

    // Things end by the guest, the staff or the scheduler: this is the planner's and the scheduler's door.
    private void finish(Event event) {
        if (event.getStatus() == EventStatus.ENDED) {
            return;
        }
        event.setStatus(EventStatus.ENDED);
        eventRepo.save(event);
        for (RestaurantTable table : tableRepo.findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(event.getId())) {
            diningSessionService.closeTable(event.getTenant().getTenantId(), table.getTableId());
        }
    }

    private void checkTimes(EventRequest request) {
        if (!request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new InvalidRequestException("The event has to end after it starts.");
        }
    }

    private void requireGuests(int guests) {
        if (guests < 1) {
            throw new InvalidRequestException("Pay for at least one guest.");
        }
    }

    private void apply(Event event, EventRequest request) {
        event.setName(request.getName().trim());
        event.setVenue(request.getVenue() == null || request.getVenue().isBlank() ? null : request.getVenue().trim());
        event.setStartsAt(request.getStartsAt());
        event.setEndsAt(request.getEndsAt());
        event.setSeatingMode(request.getSeatingMode());
        event.setPlatesPerGuest(request.getPlatesPerGuest());
        event.setExpectedGuests(request.getExpectedGuests());
    }

    private EventResponse respond(Event event) {
        return EventResponse.of(event, guestRepo.countByEvent_Id(event.getId()));
    }
}
