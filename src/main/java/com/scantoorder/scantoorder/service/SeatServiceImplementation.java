package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.UpdateSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.ReleaseSeatResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.dtos.respond.SeatStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.UpdateSeatResponse;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SeatTakenException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SeatServiceImplementation implements SeatService {
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private SeatLedger seatLedger;

    @Override
    public List<SeatStatusResponse> viewAllSeatStatus() {
        List<Seat> allSeats = seatRepo.findAll();
        List<SeatStatusResponse> responses = new ArrayList<>();
        for (Seat seat : allSeats) {
            responses.add(new SeatStatusResponse(seat.getSeatId(), seat.getSeatNumber(), seat.getStatus().name()));
        }
        return responses;
    }

    @Transactional
    @Override
    public UpdateSeatResponse updateSeat(UpdateSeatRequest updateSeatRequest) {
        if (!"VACANT".equals(updateSeatRequest.getNewState())) {
            throw new SeatStatusException("Staff can only free a seat. Guests take seats themselves.");
        }
        ReleaseSeatResponse released = diningSessionService.freeSeat(updateSeatRequest.getSeatId());
        return new UpdateSeatResponse(released.getMessage(), released.getSeatId(), released.getSeatNumber(), released.getStatus());
    }

    @Transactional
    @Override
    public SeatClaimedResponse claimSeat(ClaimSeatRequest claimSeatRequest, CustomerPrincipal caller) {
        Seat seat = lockSeatAtOpenTable(claimSeatRequest.getSeatId());
        if (!Objects.equals(seat.getTableId().getTableNumber(), claimSeatRequest.getTableId())) {
            throw new TableNotFoundException(TABLE_NOT_AVAILABLE);
        }
        SeatClaimedResponse alreadySeated = returnToOwnSeat(caller, seat.getTableId());
        if (alreadySeated != null) return alreadySeated;

        Optional<SessionSeat> current = seatLedger.current(seat.getSeatId());
        if (current.isPresent()) {
            if (current.get().getStatus() == SessionSeatStatus.HELD) {
                throw new SeatTakenException("This seat is being held for someone. Enter the join code to take it, or pick another seat.");
            }
            throw new SeatTakenException(isTableFull(seat.getTableId())
                    ? "This table is full. Ask a waiter and we will find you a seat."
                    : "That seat was just taken. Pick another one.");
        }

        DinningSession session = new DinningSession();
        session.setTableId(seat.getTableId());
        session = dinningSessionRepo.save(session);
        SessionSeat membership = seatLedger.occupy(session, seat, claimSeatRequest.getCustomerName(),
                claimSeatRequest.getCustomerPhoneNumber(), claimSeatRequest.getCustomerEmail());
        return seated("Seat claimed successfully", membership, true);
    }

    @Transactional
    @Override
    public ReleaseSeatResponse releaseSeat(String seatId) {
        return diningSessionService.freeSeat(seatId);
    }

    private static final String TABLE_NOT_AVAILABLE = "This table is not available";

    private Seat lockSeatAtOpenTable(String seatId) {
        Seat seat = seatRepo.findForUpdate(seatId).orElseThrow(() -> new SeatNotFoundException("We cannot find that seat"));
        RestaurantTable table = seat.getTableId();
        if (table == null || !table.isActive() || !seat.isActive()) {
            throw new TableNotFoundException(TABLE_NOT_AVAILABLE);
        }
        return seat;
    }

    // A guest who already has a seat goes back to it at their own table, and is turned away anywhere else.
    private SeatClaimedResponse returnToOwnSeat(CustomerPrincipal caller, RestaurantTable table) {
        if (caller == null || caller.getSessionSeatId() == null) return null;
        SessionSeat mine = sessionSeatRepo.findById(caller.getSessionSeatId()).orElse(null);
        if (mine == null || mine.getStatus() != SessionSeatStatus.OCCUPIED
                || mine.getSession().getSessionStatus() != DinningSessionStatus.ACTIVE) {
            return null;
        }
        if (!Objects.equals(mine.getSeat().getTableId().getTableId(), table.getTableId())) {
            throw new SeatStatusException("You already have a seat at another table. Leave it first, then scan this table again.");
        }
        return seated("You are already seated at this table", mine, false);
    }

    private boolean isTableFull(RestaurantTable table) {
        for (Seat seat : seatRepo.findByTableIdOrderByPositionAsc(table)) {
            if (seat.isActive() && sessionSeatRepo.findBySeatAndStatusIn(seat.getSeatId(), SeatLedger.ACTIVE)
                    .filter(membership -> !seatLedger.isExpired(membership)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private SeatClaimedResponse seated(String message, SessionSeat membership, boolean issueToken) {
        Seat seat = membership.getSeat();
        RestaurantTable table = seat.getTableId();
        DinningSession session = membership.getSession();
        SeatClaimedResponse response = new SeatClaimedResponse();
        response.setMessage(message);
        response.setSessionId(session.getSessionId());
        response.setJoinCode(session.getJoinCode());
        response.setSeatId(seat.getSeatId());
        response.setSeatNumber(seat.getSeatNumber());
        response.setPosition(seat.getPosition());
        response.setTableNumber(table.getTableNumber());
        response.setDisplayNumber(table.getDisplayNumber());
        if (issueToken) {
            response.setToken(jwtService.generateCustomerToken(session.getSessionId(), seat.getSeatId(), table.getTableId(), membership.getId()));
        }
        return response;
    }
}
