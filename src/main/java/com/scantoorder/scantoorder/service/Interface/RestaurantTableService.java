package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;

import java.util.List;

public interface RestaurantTableService {

    CreateRestaurantTableResponse createTable(int seatCapacity);

    TableStatus syncTableStatus(String tableId);

    List<RestaurantTable> viewAllTable();

    ViewTableAndSeatAvailabilityResponse viewTableAndSeatAvailability(String tableNumber);

    MenuResponse generateMenu();

    byte[] getQrCode(String tableNumber);

    void deleteTable(String tableId);
}
