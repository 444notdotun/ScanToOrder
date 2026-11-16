package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.request.EventGuestRequest;
import com.scantoorder.scantoorder.dtos.request.EventPlateRequest;
import com.scantoorder.scantoorder.dtos.request.EventRequest;
import com.scantoorder.scantoorder.dtos.respond.EventGuestResponse;
import com.scantoorder.scantoorder.dtos.respond.EventPlateResponse;
import com.scantoorder.scantoorder.dtos.respond.EventResponse;
import com.scantoorder.scantoorder.dtos.respond.EventTableResponse;
import com.scantoorder.scantoorder.service.Interface.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Builds events for tests through the real services, so the state is the state the app would make.
 */
@Component
public class EventTestSupport {
    public static final String PLANNER_SLUG = "plannerplace";
    public static final String OTHER_PLANNER_SLUG = "otherplanner";

    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventService eventService;
    @Autowired
    private EventPlateService plateService;
    @Autowired
    private EventTableService tableService;
    @Autowired
    private EventGuestService guestService;

    public Tenant planner() {
        return plannerTenant(PLANNER_SLUG, "Planner Place");
    }

    public Tenant otherPlanner() {
        return plannerTenant(OTHER_PLANNER_SLUG, "Other Planner");
    }

    private Tenant plannerTenant(String slug, String name) {
        Tenant tenant = tenants.tenant(slug, name);
        tenant.setType(TenantType.EVENT_PLANNER);
        tenant.setOpenForOrders(false);
        tenant.setPaystackSubaccountCode(null);
        return tenantRepo.save(tenant);
    }

    public EventRequest request(EventSeatingMode mode, Integer platesPerGuest) {
        EventRequest request = new EventRequest();
        request.setName("Test Wedding");
        request.setVenue("The Hall");
        request.setStartsAt(LocalDateTime.now().minusHours(1));
        request.setEndsAt(LocalDateTime.now().plusHours(5));
        request.setSeatingMode(mode);
        request.setPlatesPerGuest(platesPerGuest);
        request.setExpectedGuests(10);
        return request;
    }

    // A draft: created and nothing paid.
    public EventResponse draft(Tenant planner, EventSeatingMode mode, Integer platesPerGuest) {
        return eventService.create(planner.getTenantId(), request(mode, platesPerGuest));
    }

    // An event paid for and running, with its guests' places, tables and plates still to add.
    public Event activeEvent(Tenant planner, EventSeatingMode mode, Integer platesPerGuest, int paidGuests) {
        Event event = eventRepo.findById(draft(planner, mode, platesPerGuest).getId()).orElseThrow();
        event.setPaidGuests(paidGuests);
        event.setStatus(EventStatus.ACTIVE);
        return eventRepo.save(event);
    }

    public EventTableResponse table(Tenant planner, Event event, int seats) {
        return tableService.create(planner.getTenantId(), event.getId(), new CreateTableRequest(seats, null));
    }

    public EventPlateResponse plate(Tenant planner, Event event, String name) {
        EventPlateRequest request = new EventPlateRequest();
        request.setName(name);
        return plateService.create(planner.getTenantId(), event.getId(), request);
    }

    public EventGuestResponse guest(Tenant planner, Event event, String name) {
        EventGuestRequest request = new EventGuestRequest();
        request.setName(name);
        return guestService.add(planner.getTenantId(), event.getId(), request);
    }
}
