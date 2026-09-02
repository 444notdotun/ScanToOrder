package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.dtos.request.CreateSessionRequest;
import com.scantoorder.scantoorder.dtos.respond.CloseSessionResponse;
import com.scantoorder.scantoorder.dtos.respond.CreateSessionResponse;
import com.scantoorder.scantoorder.exception.SeatNotFoundException;
import com.scantoorder.scantoorder.exception.SessionNotFoundException;
import com.scantoorder.scantoorder.exception.TableNotFoundException;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiningSessionServiceImpl implements DiningSessionService {

    @Autowired
    private DinningSessionRepo dinningSessionRepo;

    @Autowired
    private SeatRepo seatRepo;

    @Autowired
    private TableRepo tableRepo;

    @Autowired
    private PaymentRepo paymentRepo;

    @Transactional
    @Override
    public CreateSessionResponse createSession(CreateSessionRequest request) {
        Seat seat = seatRepo.findSeatBySeatId(request.getSeatId())
                .orElseThrow(() -> new SeatNotFoundException("Seat not found"));
        RestaurantTable table = tableRepo.findByTableNumber(request.getTableNumber())
                .orElseThrow(() -> new TableNotFoundException("Table not found"));

        DinningSession dinningSession = new DinningSession();
        dinningSession.getSeats().add(seat);
        dinningSession.setCustomerName(request.getCustomerName());
        dinningSession.setCustomerPhone(request.getCustomerPhone());
        dinningSession.setCustomerEmail(request.getCustomerEmail());
        dinningSession.setTableId(table);
        dinningSession = dinningSessionRepo.save(dinningSession);

        CreateSessionResponse response = new CreateSessionResponse();
        response.setSessionId(dinningSession.getSessionId());
        response.setTableId(table.getTableId());
        response.setSeatId(seat.getSeatId());
        return response;
    }

    @Transactional
    @Override
    public CloseSessionResponse closeSession(String sessionId) {
        DinningSession session = dinningSessionRepo.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found with ID: " + sessionId));

        session.setSessionStatus(DinningSessionStatus.CLOSED);
        session.setCompletedAt(LocalDateTime.now().toString());
        dinningSessionRepo.save(session);

        if (session.getSeats() != null) {
            for (Seat seat : session.getSeats()) {
                seat.setStatus(SeatStatus.VACANT);
                seatRepo.save(seat);
            }
        }

        CloseSessionResponse response = new CloseSessionResponse();
        response.setMessage("Session closed successfully");
        response.setSessionId(sessionId);
        response.setStatus(DinningSessionStatus.CLOSED.name());
        return response;
    }
    
    @Transactional
    @Override
    public CloseSessionResponse closeSessionByTableId(String tableId) {
        DinningSession session = dinningSessionRepo.findFirstByTableId_TableIdAndSessionStatusOrderByCreatedAtDesc(
                tableId, DinningSessionStatus.ACTIVE)
                .orElseThrow(() -> new SessionNotFoundException("Active session not found for table ID: " + tableId));

        session.setSessionStatus(DinningSessionStatus.CLOSED);
        session.setCompletedAt(LocalDateTime.now().toString());
        dinningSessionRepo.save(session);

        if (session.getSeats() != null) {
            for (Seat seat : session.getSeats()) {
                seat.setStatus(SeatStatus.VACANT);
                seatRepo.save(seat);
            }
        }

        CloseSessionResponse response = new CloseSessionResponse();
        response.setMessage("Session closed successfully");
        response.setSessionId(session.getSessionId());
        response.setStatus(DinningSessionStatus.CLOSED.name());
        return response;
    }




    @Override
    public List<String> getSessionPaymentReferences(String sessionId) {
        List<Payment> payments = paymentRepo.findByOrderDinningSessionSessionIdAndStatus(
            sessionId, PaymentStatus.SUCCESSFUL
        );
        return payments.stream().map(Payment::getReference).collect(Collectors.toList());
    }

    public long getActiveSessionsCount() {
        return dinningSessionRepo.countBySessionStatus(DinningSessionStatus.ACTIVE);
    }

    @Transactional
    @Override
    public String createSession(String seatId, String tableNumber, String customerName, String customerPhone, String customerEmail) {
        Seat seat = seatRepo.findSeatBySeatId(seatId)
                .orElseThrow(() -> new SeatNotFoundException("Seat not found"));
        RestaurantTable table = tableRepo.findByTableNumber(tableNumber)
                .orElseThrow(() -> new TableNotFoundException("Table not found"));

        DinningSession dinningSession = new DinningSession();
        dinningSession.getSeats().add(seat);
        dinningSession.setCustomerName(customerName);
        dinningSession.setCustomerPhone(customerPhone);
        dinningSession.setCustomerEmail(customerEmail);
        dinningSession.setTableId(table);
        dinningSessionRepo.save(dinningSession);
        return dinningSession.getSessionId();
    }
}
