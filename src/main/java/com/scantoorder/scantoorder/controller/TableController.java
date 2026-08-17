package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.TableService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TableController {
    @Autowired
    private TableService tableService;

    @PostMapping("/RestuarantTables")
    public ResponseEntity<ApiResponse<?>> createTable(@Min (value = 4, message = "seat capacity can not be less than 4")@RequestBody int seatCapacity){
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(tableService.createTable(seatCapacity)));
    }
    @GetMapping("/RestuarantTables")
    public ResponseEntity<ApiResponse<?>> getAllTable(){
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(tableService.viewAllTable()));
    }

    @GetMapping("{tableNumber}/seatMap")
    public ResponseEntity<ApiResponse<?>> getTableSeatMap(@NotNull(message = "can not be null") @PathVariable String tableNumber){
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(tableService.viewTableAndSeatAvailability(tableNumber)));
    }

    @GetMapping("/{tableNumber}TableStatus")
    public ResponseEntity<ApiResponse<?>> getTableStatus(@PathVariable String tableNumber){
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(tableService.syncTableStatus(tableNumber)));
    }

}
