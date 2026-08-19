package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

  @Autowired
  private RedisTemplate<String,Object> redisTemplate;

  private static final String MENU_KEY = "menu:active";






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
        Object cachedMenu = redisTemplate.opsForValue().get(MENU_KEY);
        if(cachedMenu!=null){
            return (MenuResponse) cachedMenu;
        }

        List<Category> categories = categoryRepository.findAllByIsActiveTrue();
        List<CategoryAndItemResponse> categoryAndItemResponseList = new ArrayList<>();

        for (Category category : categories) {
            CategoryAndItemResponse categoryResponse = new CategoryAndItemResponse();
            categoryResponse.setCategoryName(category.getCategoryName());
            List<Item> items = itemRepo.findAllByCategoryIdAndIsAvailableTrue(category);
            List<ItemResponse> itemResponses = items.stream()
                    .map(item -> modelMapper.map(item, ItemResponse.class))
                    .toList();

            categoryResponse.setItemResponse(itemResponses);
            categoryAndItemResponseList.add(categoryResponse);
        }


        MenuResponse menuResponse = new MenuResponse();
        menuResponse.setCategoryAndItemResponse(categoryAndItemResponseList);

        redisTemplate.opsForValue().set(MENU_KEY, menuResponse,24, TimeUnit.HOURS);
        return menuResponse;
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
