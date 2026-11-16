package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.TableResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;

import java.util.List;

public interface RestaurantTableService {
    CreateRestaurantTableResponse createTable(String tenantId, int seatCapacity);

    CreateRestaurantTableResponse createTable(String tenantId, CreateTableRequest request);

    // For a table made for one event (null for a restaurant): numbered from 1 inside the event.
    CreateRestaurantTableResponse createTable(String tenantId, CreateTableRequest request, com.scantoorder.scantoorder.data.model.Event event);

    // What the table's QR code points at.
    String scanLink(String tenantId, String tableNumber);

    List<TableResponse> viewAllTable(String tenantId);

    ViewTableAndSeatAvailabilityResponse viewTableAndSeatAvailability(String tenantId, String tableNumber);

    ViewTableAndSeatAvailabilityResponse publicSeatMap(String slug, int displayNumber);

    byte[] getQrCode(String tenantId, String tableNumber);

    void deleteTable(String tenantId, String tableId);
}
