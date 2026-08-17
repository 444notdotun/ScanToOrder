package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.TableStatus;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.respond.CreateRestaurantTableResponse;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableSeatResponse;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TableService implements RestaurantTableService{
  @Autowired
  private  SeatRepo seatRepo;
  @Autowired
  private TableRepo tableRepo;
  @Autowired
  ModelMapper modelMapper;
  @Autowired
  private SeatService seatService;
  @Autowired
    private CategoryRepository categoryRepository;
  @Autowired
  private ItemRepo itemRepo;






    @Override
    public CreateRestaurantTableResponse createTable(int seatCapacity) {
        RestaurantTable restaurantTable = new RestaurantTable();
        restaurantTable.setCapacity(seatCapacity);
        tableRepo.save(restaurantTable);
        for(int i=0;i<seatCapacity;i++){
            Seat seat = new Seat();
            seat.setTableId(restaurantTable);
            seatRepo.save(seat);
        }
        String message= "Table created successfully";
        CreateRestaurantTableResponse createRestaurantTableResponse= modelMapper.map(restaurantTable, CreateRestaurantTableResponse.class);
        createRestaurantTableResponse.setMessage(message);
        return createRestaurantTableResponse;
    }

    @Override
    public TableStatus syncTableStatus(String tableNumber) {
        if(!isTableAvailable(tableNumber)) return TableStatus.OCCUPIED;
        return TableStatus.AVAILABLE;
    }

    @Override
    public List<RestaurantTable> viewAllTable() {
        return tableRepo.findAll();
    }

    @Override
    public ViewTableAndSeatAvailabilityResponse viewTableAndSeatAvailability(String tableId) {
       Optional<RestaurantTable> table = Optional.of(tableRepo.findByTableNumber(tableId)).orElseThrow(() -> new TableNotFoundException("Table Not Found"));
       ViewTableAndSeatAvailabilityResponse response =modelMapper.map(table.get(), ViewTableAndSeatAvailabilityResponse.class);
       List<ViewTableSeatResponse> viewTableSeatResponse =serializeViewTable(table);
       response.setSeats(viewTableSeatResponse);
       return response;
    }

    @Override
    public MenuResponse generateMenu() {
        List<Category> categories = categoryRepository.findAll();
        for(Category newCategory:categories){
            itemRepo.

        }

        return null;
    }

    private List<ViewTableSeatResponse> serializeViewTable(Optional<RestaurantTable> table) {
        Optional<List<Seat>> seats = Optional.of(seatRepo.findSeatByTableId(table.get()).orElseThrow(() -> new TableNotFoundException("Table not found")));
        List<ViewTableSeatResponse> viewTableSeatResponseList = new ArrayList<>();
        for(Seat newSeat:seats.get()){
           viewTableSeatResponseList.add(modelMapper.map(newSeat, ViewTableSeatResponse.class));
        }
        return viewTableSeatResponseList;
    }

    private boolean isTableAvailable(String tableNumber) {
        return seatService.SeatTableSync(tableNumber);
    }

}
