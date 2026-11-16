package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventPlate;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.repository.EventPlateRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.dtos.request.EventPlateRequest;
import com.scantoorder.scantoorder.dtos.respond.EventPlateResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * An event's menu: plates the planner writes. No prices; one order is exactly one plate.
 */
@Service
public class EventPlateService {

    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventPlateRepo plateRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;

    @Transactional(readOnly = true)
    public List<EventPlateResponse> list(String tenantId, String eventId) {
        event(tenantId, eventId);
        return plateRepo.findByEvent_IdOrderByNameAsc(eventId).stream().map(EventPlateResponse::of).toList();
    }

    @Transactional
    public EventPlateResponse create(String tenantId, String eventId, EventPlateRequest request) {
        Event event = open(tenantId, eventId);
        EventPlate plate = new EventPlate();
        plate.setTenant(event.getTenant());
        plate.setEvent(event);
        apply(plate, request);
        return EventPlateResponse.of(plateRepo.save(plate));
    }

    @Transactional
    public EventPlateResponse update(String tenantId, String eventId, String plateId, EventPlateRequest request) {
        open(tenantId, eventId);
        EventPlate plate = plateRepo.findByIdAndEvent_Id(plateId, eventId).orElseThrow(() -> new ResourceNotFoundException(PLATE_NOT_FOUND));
        apply(plate, request);
        return EventPlateResponse.of(plateRepo.save(plate));
    }

    // A plate that has been ordered is part of the record: it can be switched off, never deleted.
    @Transactional
    public void delete(String tenantId, String eventId, String plateId) {
        open(tenantId, eventId);
        EventPlate plate = plateRepo.findByIdAndEvent_Id(plateId, eventId).orElseThrow(() -> new ResourceNotFoundException(PLATE_NOT_FOUND));
        if (orderItemRepo.existsByPlate_Id(plateId)) {
            throw new ConflictException("This plate has been ordered, so it can't be deleted. Switch it off instead.");
        }
        plateRepo.delete(plate);
    }

    public static final String PLATE_NOT_FOUND = "We cannot find that plate";

    private void apply(EventPlate plate, EventPlateRequest request) {
        plate.setName(request.getName().trim());
        plate.setDescription(request.getDescription() == null || request.getDescription().isBlank() ? null : request.getDescription().trim());
        if (request.getAvailable() != null) {
            plate.setAvailable(request.getAvailable());
        }
    }

    private Event event(String tenantId, String eventId) {
        return eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(EventServiceImpl.NOT_FOUND));
    }

    private Event open(String tenantId, String eventId) {
        Event event = event(tenantId, eventId);
        if (event.getStatus() == EventStatus.ENDED) {
            throw new ConflictException("This event is over, so its plates can't be changed.");
        }
        return event;
    }
}
