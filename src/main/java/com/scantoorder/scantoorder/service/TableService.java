package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TableService implements RestaurantTableService {
  @Autowired
  private  SeatRepo seatRepo;
  @Autowired
  private TableRepo tableRepo;
  @Autowired
  ModelMapper modelMapper;
  @Autowired
  private SeatService seatService;

  @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
  private String frontendUrl;

    @Autowired
    private MenuService menuService;








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
        return menuService.generateMenu();
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

    @Override
    public byte[] getQrCode(String tableNumber) {
        RestaurantTable table = tableRepo.findByTableNumber(tableNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);

            String url = frontendUrl + "/table/" + tableNumber;
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 350, 350, hints);

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code for table " + tableNumber, e);
        }
    }

    @Override
    @Transactional
    public void deleteTable(String tableId) {
        RestaurantTable table = tableRepo.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        seatRepo.findSeatByTableId(table).ifPresent(seats -> {
            seatRepo.deleteAll(seats);
        });
        tableRepo.delete(table);
    }
}
