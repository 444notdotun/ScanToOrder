package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TableService implements RestaurantTableService{
  @Autowired
  private  SeatRepo seatRepo;
  @Autowired
  private TableRepo tableRepo;
  @Autowired
  private SeatService seatService;
    @Override
    public String createTable(int seatCapacity) {
        RestaurantTable restaurantTable = new RestaurantTable();
        restaurantTable.setCapacity(seatCapacity);
        tableRepo.save(restaurantTable);
        for(int i=0;i<seatCapacity;i++){
            Seat seat = new Seat();
            seat.setTableId(restaurantTable);
            seatRepo.save(seat);
        }
        return "Table created successfully";
    }

    @Override
    public TableStatus syncTableStatus(String tableId) {
        if(!isTableAvailable(tableId)) return TableStatus.OCCUPIED;
        return TableStatus.AVAILABLE;
    }

    private boolean isTableAvailable(String tableId) {
        return seatService.SeatTableSync(tableId);
    }

}
