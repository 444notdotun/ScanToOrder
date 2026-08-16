package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SeatStatus;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SeatServiceImplementation implements SeatService{
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private TableRepo tableRepo;

    @Override
    public String viewAllSeatStatus() {
        List<Seat> allSeats =seatRepo.findAll();
        StringBuilder seatStatus= new StringBuilder();
        for(Seat seat:allSeats){
            seatStatus.append(seat.getSeatNumber()).append(" ").append(seat.getStatus().toString()).append(" \n");
        }
        return seatStatus.toString();
    }

    @Override
    public boolean SeatTableSync(String tableId) {
        Optional<RestaurantTable> table = Optional.of(tableRepo.findById(tableId).orElseThrow(() -> new TableNotFoundException("table not found")));
        List<Seat> seats = seatRepo.findSeatByTableId(table.get());
        int totalSeat = seats.size();
        int counter = 0;
        for(Seat newSeat:seats){
         if(newSeat.getStatus()== SeatStatus.OCCUPIED||newSeat.getStatus()==SeatStatus.HELD){
            counter++;
         }
        }
        return counter != totalSeat;
    }

    @Override
    public String updateSeat(String update, String seatId) {
        Seat seat =seatRepo.findSeatBySeatId(seatId).orElseThrow(()-> new SeatNotFoundException("seat not found"));
        seat.setStatus(SeatStatus.valueOf(update));
        seatRepo.save(seat);
        return "your"+seat.getSeatNumber()+" is claimed";
    }
}
