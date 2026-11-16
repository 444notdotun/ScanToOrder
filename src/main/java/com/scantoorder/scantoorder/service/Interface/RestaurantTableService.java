package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.TableResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;

import java.util.List;

public interface RestaurantTableService {
    CreateRestaurantTableResponse createTable(String tenantId, int seatCapacity);

    CreateRestaurantTableResponse createTable(String tenantId, CreateTableRequest request);

    List<TableResponse> viewAllTable(String tenantId);

    ViewTableAndSeatAvailabilityResponse viewTableAndSeatAvailability(String tenantId, String tableNumber);

    ViewTableAndSeatAvailabilityResponse publicSeatMap(String slug, int displayNumber);

    byte[] getQrCode(String tenantId, String tableNumber);

    void deleteTable(String tenantId, String tableId);
}
