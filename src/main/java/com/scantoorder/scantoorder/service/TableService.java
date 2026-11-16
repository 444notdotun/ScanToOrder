package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.CreateTableRequest;
import com.scantoorder.scantoorder.dtos.respond.*;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TableService implements RestaurantTableService {
  @Autowired
  private  SeatRepo seatRepo;
  @Autowired
  private TableRepo tableRepo;
  @Autowired
  private TenantRepo tenantRepo;
  @Autowired
  private SessionSeatRepo sessionSeatRepo;
  @Autowired
  private SeatLedger seatLedger;
  @Autowired
  private TenantAccess tenantAccess;
  @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
  private String frontendUrl;

    private static final String TABLE_NOT_AVAILABLE = "This table is not available";

    @Override
    @Transactional
    public CreateRestaurantTableResponse createTable(String tenantId, int seatCapacity) {
        return createTable(tenantId, new CreateTableRequest(seatCapacity, null));
    }

    @Override
    @Transactional
    public CreateRestaurantTableResponse createTable(String tenantId, CreateTableRequest request) {
        int seatCapacity = request.getSeatCapacity();
        if (seatCapacity < CreateTableRequest.MIN_CAPACITY || seatCapacity > CreateTableRequest.MAX_CAPACITY) {
            throw new InvalidRequestException("A table needs between " + CreateTableRequest.MIN_CAPACITY
                    + " and " + CreateTableRequest.MAX_CAPACITY + " seats");
        }
        Tenant tenant = tenantRepo.getReferenceById(tenantId);
        RestaurantTable restaurantTable = new RestaurantTable();
        restaurantTable.setTenant(tenant);
        restaurantTable.setCapacity(seatCapacity);
        restaurantTable.setDisplayNumber(chooseDisplayNumber(tenantId, request.getDisplayNumber()));
        tableRepo.save(restaurantTable);
        for(int position=1;position<=seatCapacity;position++){
            Seat seat = new Seat();
            seat.setTenant(tenant);
            seat.setTableId(restaurantTable);
            seat.setPosition(position);
            seatRepo.save(seat);
        }
        CreateRestaurantTableResponse response = new CreateRestaurantTableResponse();
        response.setTableId(restaurantTable.getTableId());
        response.setTableNumber(restaurantTable.getTableNumber());
        response.setDisplayNumber(restaurantTable.getDisplayNumber());
        response.setCapacity(seatCapacity);
        response.setStatus(TableStatus.AVAILABLE);
        response.setMessage("Table created successfully");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> viewAllTable(String tenantId) {
        List<TableResponse> responses = new ArrayList<>();
        for (RestaurantTable table : tableRepo.findAllActive(tenantId)) {
            responses.add(new TableResponse(table.getTableId(), table.getTableNumber(), table.getDisplayNumber(),
                    table.getCapacity(), statusOf(table), table.isActive()));
        }
        return responses;
    }

    // For staff: the table is named by its code, and only a table of the caller's business is found.
    @Override
    @Transactional(readOnly = true)
    public ViewTableAndSeatAvailabilityResponse viewTableAndSeatAvailability(String tenantId, String tableNumber) {
        RestaurantTable table = tableRepo.findByTableNumberAndTenant_TenantId(tableNumber, tenantId)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        return seatMapOf(table);
    }

    // For a guest who scanned the QR code: the link names the business and the table's number.
    @Override
    @Transactional(readOnly = true)
    public ViewTableAndSeatAvailabilityResponse publicSeatMap(String slug, int displayNumber) {
        Tenant tenant = tenantAccess.openTenantBySlug(slug);
        RestaurantTable table = tableRepo.findByTenant_TenantIdAndDisplayNumber(tenant.getTenantId(), displayNumber)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(TABLE_NOT_AVAILABLE));
        return seatMapOf(table);
    }

    private ViewTableAndSeatAvailabilityResponse seatMapOf(RestaurantTable table) {
        Set<String> expiredHolds = expiredHoldSeatIds(table);
        List<ViewTableSeatResponse> seats = new ArrayList<>();
        boolean anyVacant = false;
        for (Seat seat : seatRepo.findByTableIdOrderByPositionAsc(table)) {
            if (!seat.isActive()) continue;
            SeatStatus status = expiredHolds.contains(seat.getSeatId()) ? SeatStatus.VACANT : seat.getStatus();
            anyVacant = anyVacant || status == SeatStatus.VACANT;
            ViewTableSeatResponse seatResponse = new ViewTableSeatResponse();
            seatResponse.setSeatId(seat.getSeatId());
            seatResponse.setSeatNumber(seat.getSeatNumber());
            seatResponse.setPosition(seat.getPosition());
            seatResponse.setStatus(status);
            seats.add(seatResponse);
        }
        ViewTableAndSeatAvailabilityResponse response = new ViewTableAndSeatAvailabilityResponse();
        response.setTableId(table.getTableId());
        response.setTableNumber(table.getTableNumber());
        response.setDisplayNumber(table.getDisplayNumber());
        response.setCapacity(table.getCapacity());
        response.setStatus(anyVacant ? TableStatus.AVAILABLE : TableStatus.OCCUPIED);
        response.setSeats(seats);
        return response;
    }

    // A table is OCCUPIED only when no seat is free. Worked out from the seats, never stored.
    private TableStatus statusOf(RestaurantTable table) {
        Set<String> expiredHolds = expiredHoldSeatIds(table);
        for (Seat seat : seatRepo.findByTableIdOrderByPositionAsc(table)) {
            if (seat.isActive() && (seat.getStatus() == SeatStatus.VACANT || expiredHolds.contains(seat.getSeatId()))) {
                return TableStatus.AVAILABLE;
            }
        }
        return TableStatus.OCCUPIED;
    }

    private Set<String> expiredHoldSeatIds(RestaurantTable table) {
        return sessionSeatRepo.findByTableAndStatusIn(table.getTableId(), List.of(SessionSeatStatus.HELD)).stream()
                .filter(seatLedger::isExpired)
                .map(membership -> membership.getSeat().getSeatId())
                .collect(Collectors.toSet());
    }

    private Integer chooseDisplayNumber(String tenantId, Integer requested) {
        if (requested != null) {
            if (requested < 1) {
                throw new InvalidRequestException("Table number must be 1 or more");
            }
            if (tableRepo.existsByTenant_TenantIdAndDisplayNumber(tenantId, requested)) {
                throw new ConflictException("Table " + requested + " already exists. Choose another number.");
            }
            return requested;
        }
        Set<Integer> used = new HashSet<>(tableRepo.findUsedDisplayNumbers(tenantId));
        int next = 1;
        while (used.contains(next)) {
            next++;
        }
        return next;
    }

    @Override
    public byte[] getQrCode(String tenantId, String tableNumber) {
        RestaurantTable table = tableRepo.findByTableNumberAndTenant_TenantId(tableNumber, tenantId)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            String url = frontendUrl + "/t/" + table.getTenant().getSlug() + "/table/" + table.getDisplayNumber();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 350, 350, hints);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code for table " + tableNumber, e);
        }
    }

    // Past orders still point at the table, so it is switched off and its number freed, not removed.
    @Override
    @Transactional
    public void deleteTable(String tenantId, String tableId) {
        RestaurantTable table = tableRepo.findByTableIdAndTenant_TenantId(tableId, tenantId)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        List<String> seatIds = seatRepo.findSeatIdsByTable(tableId);
        List<Seat> seats = seatIds.isEmpty() ? List.of() : seatRepo.findAllForUpdate(seatIds);
        for (Seat seat : seats) {
            if (seatLedger.current(seat.getSeatId()).isPresent()) {
                throw new ConflictException("Guests are still at this table. Close the table first, then remove it.");
            }
        }
        for (Seat seat : seats) {
            seat.setActive(false);
            seat.setStatus(SeatStatus.VACANT);
        }
        seatRepo.saveAll(seats);
        table.setActive(false);
        table.setDisplayNumber(null);
        tableRepo.save(table);
    }
}
