package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.UpdateSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
@Slf4j
@Service
public class SeatServiceImplementation implements SeatService {
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private ModelMapper modelMapper;

    @Override
    public List<SeatStatusResponse> viewAllSeatStatus() {
        List<Seat> allSeats = seatRepo.findAll();
        List<SeatStatusResponse> responses = new ArrayList<>();
        for (Seat seat : allSeats) {
            responses.add(new SeatStatusResponse(seat.getSeatId(), seat.getSeatNumber(), seat.getStatus().name()));
        }
        return responses;
    }

    @Override
    public boolean SeatTableSync(String tableNumber) {
        Optional<RestaurantTable> table = Optional.of(tableRepo.findByTableNumber(tableNumber).orElseThrow(() -> new TableNotFoundException("table not found")));
        Optional<List<Seat>> seats = seatRepo.findSeatByTableId(table.get());
        int totalSeat = seats.get().size();
        int counter = 0;
        for (Seat newSeat : seats.get()) {
            if (newSeat.getStatus() == SeatStatus.OCCUPIED || newSeat.getStatus() == SeatStatus.HELD) {
                counter++;
            }
        }
        return counter != totalSeat;
    }

    @Override
    public UpdateSeatResponse updateSeat(UpdateSeatRequest updateSeatRequest) {
        Seat seat = seatRepo.findSeatBySeatNumber(updateSeatRequest.getSeatId()).orElseThrow(() -> new SeatNotFoundException("seat not found"));
        if (!isSeatStateValid(seat.getStatus().toString(), updateSeatRequest.getNewState())) throw new SeatStatusException("seat Can Not Move to  This State");
        switch (updateSeatRequest.getNewState()) {
            case "OCCUPIED" -> seat.setStatus(SeatStatus.OCCUPIED);
            case "VACANT" -> seat.setStatus(SeatStatus.VACANT);
            case "HELD" -> seat.setStatus(SeatStatus.HELD);
        }
        seatRepo.save(seat);
        return new UpdateSeatResponse("your" + seat.getSeatNumber() + " is updated", seat.getSeatId(), seat.getSeatNumber(), seat.getStatus().name());
    }

    @Transactional
    @Override
    public SeatClaimedResponse claimSeat(ClaimSeatRequest claimSeatRequest) {
        UpdateSeatRequest updateSeatRequest = modelMapper.map(claimSeatRequest, UpdateSeatRequest.class);
        updateSeatRequest.setNewState("OCCUPIED");
        updateSeat(updateSeatRequest);
        Seat seat = seatRepo.findSeatBySeatNumber(claimSeatRequest.getSeatId()).orElseThrow(() -> new SeatNotFoundException("seat not found"));
        if(!Objects.equals(seat.getTableId().getTableNumber(), claimSeatRequest.getTableId()))throw new TableNotFoundException("table not found");
        String dinningSessionsId = diningSessionService.createSession(seat.getSeatId(), seat.getTableId().getTableNumber(), claimSeatRequest.getCustomerName(), claimSeatRequest.getCustomerPhoneNumber(), claimSeatRequest.getCustomerEmail());
        String token = jwtService.generateCustomerToken(dinningSessionsId, seat.getSeatId(), seat.getTableId().getTableId());
        log.info("Generated token for seat {}: {}", seat.getSeatNumber(), token);
        SeatClaimedResponse seatClaimedResponse = new SeatClaimedResponse();
        seatClaimedResponse.setToken(token);
        seatClaimedResponse.setMessage("Seat claimed successfully");
        return seatClaimedResponse;
    }

    @Transactional
    @Override
    public ReleaseSeatResponse releaseSeat(String seatId) {
        Seat seat = seatRepo.findSeatBySeatId(seatId).orElseThrow(() -> new SeatNotFoundException("seat not found"));
        seat.setStatus(SeatStatus.VACANT);
        seatRepo.save(seat);
        return new ReleaseSeatResponse("Seat released successfully", seat.getSeatId(), seat.getSeatNumber(), seat.getStatus().name());
    }

    private boolean isSeatStateValid(String currentState, String newState) {
        return switch (currentState) {
            case "VACANT" -> newState.equals("OCCUPIED") || newState.equals("HELD");
            case "OCCUPIED" -> newState.equals("VACANT");
            case "HELD" -> newState.equals("VACANT") || newState.equals("OCCUPIED");
            default -> false;
        };
    }
}
