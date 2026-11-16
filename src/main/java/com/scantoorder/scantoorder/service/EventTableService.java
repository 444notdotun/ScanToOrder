package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.repository.EventGuestRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.EventTableResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tables for one event, made like a restaurant's and with the same QR link. A table belongs to one event.
 */
@Service
public class EventTableService {

    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private EventGuestRepo guestRepo;
    @Autowired
    private RestaurantTableService tableService;

    @Transactional(readOnly = true)
    public List<EventTableResponse> list(String tenantId, String eventId) {
        find(tenantId, eventId);
        return tableRepo.findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(eventId).stream()
                .map(table -> new EventTableResponse(table.getTableId(), table.getTableNumber(), table.getDisplayNumber(), table.getCapacity()))
                .toList();
    }

    // Numbers are one sequence per business, because the QR link names a table by its number alone.
    @Transactional
    public EventTableResponse create(String tenantId, String eventId, CreateTableRequest request) {
        Event event = find(tenantId, eventId);
        if (event.getStatus() == EventStatus.ENDED) {
            throw new ConflictException("This event is over, so tables can't be added.");
        }
        CreateRestaurantTableResponse created = tableService.createTable(tenantId, request);
        RestaurantTable table = tableRepo.findByTableIdAndTenant_TenantId(created.getTableId(), tenantId).orElseThrow();
        table.setEvent(event);
        tableRepo.save(table);
        return new EventTableResponse(table.getTableId(), table.getTableNumber(), table.getDisplayNumber(), table.getCapacity());
    }

    @Transactional
    public void delete(String tenantId, String eventId, String tableId) {
        find(tenantId, eventId);
        RestaurantTable table = tableRepo.findByTableIdAndTenant_TenantId(tableId, tenantId)
                .filter(found -> found.getEvent() != null && found.getEvent().getId().equals(eventId))
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        for (Seat seat : seatRepo.findByTableIdOrderByPositionAsc(table)) {
            if (!guestRepo.findByAssignedSeat_SeatId(seat.getSeatId()).isEmpty()) {
                throw new ConflictException("Guests are assigned to this table. Move or remove them first.");
            }
        }
        tableService.deleteTable(tenantId, tableId);
    }

    private Event find(String tenantId, String eventId) {
        return eventRepo.findByIdAndTenant_TenantId(eventId, tenantId).orElseThrow(() -> new ResourceNotFoundException(EventServiceImpl.NOT_FOUND));
    }
}
