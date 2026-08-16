package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.TableStatus;

public interface RestaurantTableService {

    String createTable(int seatCapacity);

    TableStatus syncTableStatus(String tableId);
}
