package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.ServiceCall;
import com.scantoorder.scantoorder.data.model.ServiceStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.data.repository.ServiceCallRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.FloorResponse;
import com.scantoorder.scantoorder.dtos.respond.FloorTableResponse;
import com.scantoorder.scantoorder.dtos.respond.ServiceOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.TodayResponse;
import com.scantoorder.scantoorder.dtos.respond.WaiterCallResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.utils.LagosTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the kitchen, the waiters and the manager look at: the pass, the ready list, calls, tables and the floor.
 * Everything is read from the caller's own business only.
 */
@Service
public class ServiceBoardService {

    public static final long COLD_AFTER_MINUTES = 20;
    private static final List<OrderStatus> VISITS = List.of(OrderStatus.UNAVAILABLE, OrderStatus.REFUND_DUE);

    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private ServiceCallRepo callRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo sessionRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private SeatLedger seatLedger;
    @Autowired
    private LiveUpdates liveUpdates;

    // ---- kitchen and waiter lists

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> kitchen(String tenantId) {
        return orders(tenantId, List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY), false);
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> ready(String tenantId) {
        return orders(tenantId, List.of(OrderStatus.READY), true);
    }

    // Orders the kitchen could not make: a waiter has to go to the table.
    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> visits(String tenantId) {
        return orders(tenantId, VISITS, false);
    }

    private List<ServiceOrderResponse> orders(String tenantId, List<OrderStatus> statuses, boolean flagCold) {
        LocalDateTime now = LocalDateTime.now();
        return orderRepo.findByTenant_TenantIdAndOrderStatusIn(tenantId, statuses).stream()
                .sorted(Comparator.comparing(this::ticketTime))
                .map(order -> response(order, now, flagCold))
                .toList();
    }

    // A swapped order is a new ticket: it takes its place in the queue from the swap.
    private LocalDateTime ticketTime(Order order) {
        if (order.getSwappedAt() != null) return order.getSwappedAt();
        return order.getPaidAt() != null ? order.getPaidAt() : order.getCreatedAt();
    }

    private ServiceOrderResponse response(Order order, LocalDateTime now, boolean flagCold) {
        ServiceOrderResponse response = new ServiceOrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());
        if (order.getTable() != null) response.setTableNumber(order.getTable().getDisplayNumber());
        if (order.getSeat() != null) response.setSeatPosition(order.getSeat().getPosition());
        if (order.getSessionSeat() != null) response.setGuestName(order.getSessionSeat().getGuestName());
        for (OrderItem line : orderItemRepo.findByOrder_OrderId(order.getOrderId())) {
            if (line.isSwappedOut()) continue;
            String name = line.getItem() != null ? line.getItem().getItemName()
                    : line.getPlate() != null ? line.getPlate().getName() : "Item";
            response.getItems().add(line.getPlate() != null ? name : line.getQuantity() + " x " + name);
        }
        response.setNote(order.getNote());
        response.setStep(order.getOrderStatus().name());
        if (order.getUnavailableReason() != null
                && (order.getOrderStatus() == OrderStatus.UNAVAILABLE || order.getOrderStatus() == OrderStatus.REFUND_DUE)) {
            response.setUnavailableReason(order.getUnavailableReason().name());
        }
        // Minutes in the step it is in now: since it was sent to the kitchen, or since it was ready.
        LocalDateTime since = order.getOrderStatus() == OrderStatus.READY && order.getReadyAt() != null
                ? order.getReadyAt() : ticketTime(order);
        long minutes = since == null ? 0 : Math.max(0, Duration.between(since, now).toMinutes());
        response.setMinutesWaiting(minutes);
        if (order.getSwappedAt() != null && (order.getOrderStatus() == OrderStatus.PAID || order.getOrderStatus() == OrderStatus.PREPARING)) {
            response.setFlag("Swapped at the table. Cook this instead.");
        }
        if (flagCold && minutes >= COLD_AFTER_MINUTES) {
            response.setFlag("Ready for " + minutes + " minutes. It is getting cold.");
        }
        return response;
    }

    // ---- calls

    @Transactional(readOnly = true)
    public List<WaiterCallResponse> calls(String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        return callRepo.findByTenant_TenantIdAndServiceStatusInOrderByCreatedAtAsc(tenantId,
                        List.of(ServiceStatus.UNASSIGNED, ServiceStatus.IN_PROGRESS)).stream()
                .map(call -> callResponse(call, now))
                .toList();
    }

    @Transactional
    public WaiterCallResponse take(String tenantId, String workerId, String callId) {
        ServiceCall call = lockCall(tenantId, callId);
        if (call.getServiceStatus() == ServiceStatus.IN_PROGRESS) {
            if (call.getTakenBy() != null && call.getTakenBy().getWorkerId().equals(workerId)) {
                return callResponse(call, LocalDateTime.now());
            }
            throw new ConflictException(firstName(call.getTakenBy()) + " has already taken " + where(call) + "'s call.");
        }
        if (call.getServiceStatus() != ServiceStatus.UNASSIGNED) {
            throw new ConflictException("This call has already been sorted.");
        }
        call.setServiceStatus(ServiceStatus.IN_PROGRESS);
        call.setTakenAt(LocalDateTime.now());
        call.setTakenBy(workerId == null ? null : workerRepo.findById(workerId).orElse(null));
        call.setAssignedWorker(call.getTakenBy());
        callRepo.save(call);
        liveUpdates.publish(tenantId, call.getSessionId() == null ? null : call.getSessionId().getSessionId(), "call", callId, "TAKEN");
        return callResponse(call, LocalDateTime.now());
    }

    @Transactional
    public WaiterCallResponse done(String tenantId, String workerId, String callId) {
        ServiceCall call = lockCall(tenantId, callId);
        if (call.getServiceStatus() == ServiceStatus.RESOLVED || call.getServiceStatus() == ServiceStatus.DISMISSED) {
            throw new ConflictException(firstName(call.getTakenBy()) + " has already sorted " + where(call) + "'s call.");
        }
        call.setServiceStatus(ServiceStatus.RESOLVED);
        call.setDoneAt(LocalDateTime.now());
        call.setResolvedAt(call.getDoneAt());
        if (call.getTakenBy() == null && workerId != null) {
            call.setTakenBy(workerRepo.findById(workerId).orElse(null));
            call.setTakenAt(call.getDoneAt());
        }
        callRepo.save(call);
        liveUpdates.publish(tenantId, call.getSessionId() == null ? null : call.getSessionId().getSessionId(), "call", callId, "DONE");
        return callResponse(call, LocalDateTime.now());
    }

    private ServiceCall lockCall(String tenantId, String callId) {
        return callRepo.findForUpdate(callId, tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find that request"));
    }

    private WaiterCallResponse callResponse(ServiceCall call, LocalDateTime now) {
        WaiterCallResponse response = new WaiterCallResponse();
        response.setCallId(call.getServiceCallId());
        if (call.getSessionId() != null && call.getSessionId().getTableId() != null) {
            response.setTableNumber(call.getSessionId().getTableId().getDisplayNumber());
        }
        response.setWhere(where(call));
        response.setWhat(call.getServiceDescription());
        response.setState(call.getServiceStatus() == ServiceStatus.UNASSIGNED ? "OPEN"
                : call.getServiceStatus() == ServiceStatus.IN_PROGRESS ? "TAKEN" : "DONE");
        LocalDateTime since = call.getCreatedAt();
        response.setMinutesWaiting(since == null ? 0 : Math.max(0, Duration.between(since, now).toMinutes()));
        if (call.getTakenBy() != null) response.setTakenBy(firstName(call.getTakenBy()));
        return response;
    }

    private String where(ServiceCall call) {
        return call.getSessionId() != null && call.getSessionId().getTableId() != null
                ? "Table " + call.getSessionId().getTableId().getDisplayNumber() : "A table";
    }

    private String firstName(Worker worker) {
        if (worker == null) return "Someone";
        String name = worker.getFullName() == null || worker.getFullName().isBlank() ? worker.getUsername() : worker.getFullName();
        return name.trim().split("\\s+")[0];
    }

    // ---- tables

    @Transactional(readOnly = true)
    public List<FloorTableResponse> tables(String tenantId) {
        return tableRepo.findAllActive(tenantId).stream().map(this::floorTable).toList();
    }

    private FloorTableResponse floorTable(RestaurantTable table) {
        FloorTableResponse response = new FloorTableResponse();
        response.setTableId(table.getTableId());
        response.setTableNumber(table.getTableNumber());
        response.setDisplayNumber(table.getDisplayNumber());
        response.setCapacity(table.getCapacity());
        response.setGroups(sessionRepo.findByTableId_TableIdAndSessionStatusOrderByCreatedAtAsc(table.getTableId(), DinningSessionStatus.ACTIVE).size());
        LocalDateTime now = LocalDateTime.now();
        List<SessionSeat> active = sessionSeatRepo.findByTableAndStatusIn(table.getTableId(), SeatLedger.ACTIVE);
        for (Seat seat : seatRepo.findByTableIdOrderByPositionAsc(table)) {
            if (!seat.isActive()) continue;
            FloorTableResponse.Seat view = new FloorTableResponse.Seat();
            view.setSeatId(seat.getSeatId());
            view.setPosition(seat.getPosition());
            SessionSeat here = active.stream().filter(m -> m.getSeat().getSeatId().equals(seat.getSeatId())).findFirst().orElse(null);
            if (here == null || seatLedger.isExpired(here)) {
                view.setStatus("VACANT");
            } else if (here.getStatus() == SessionSeatStatus.HELD) {
                view.setStatus("HELD");
                view.setSecondsLeft(here.getHeldUntil() == null ? 0 : Math.max(0, Duration.between(now, here.getHeldUntil()).getSeconds()));
            } else {
                view.setStatus("OCCUPIED");
                String name = here.getGuestName();
                view.setGuestFirstName(name == null || name.isBlank() ? null : name.trim().split("\\s+")[0]);
            }
            response.getSeats().add(view);
        }
        return response;
    }

    // ---- the day and the floor

    @Transactional(readOnly = true)
    public TodayResponse today(String tenantId) {
        LagosTime.Range today = LagosTime.today();
        return new TodayResponse(orderRepo.countByTenant_TenantIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(tenantId, today.from(), today.to()));
    }

    @Transactional(readOnly = true)
    public FloorResponse floor(String tenantId) {
        FloorResponse floor = new FloorResponse();
        floor.setTables(tables(tenantId));
        floor.setPaid(orderRepo.countByTenant_TenantIdAndOrderStatus(tenantId, OrderStatus.PAID));
        floor.setCooking(orderRepo.countByTenant_TenantIdAndOrderStatus(tenantId, OrderStatus.PREPARING));
        floor.setReady(orderRepo.countByTenant_TenantIdAndOrderStatus(tenantId, OrderStatus.READY));
        floor.setNeedVisit(VISITS.stream().mapToLong(status -> orderRepo.countByTenant_TenantIdAndOrderStatus(tenantId, status)).sum());
        List<WaiterCallResponse> calls = calls(tenantId);
        floor.setCalls(calls);
        floor.setOpenCalls(calls.stream().filter(c -> c.getState().equals("OPEN")).count());
        floor.setTakenCalls(calls.stream().filter(c -> c.getState().equals("TAKEN")).count());
        LocalDateTime now = LocalDateTime.now();
        var open = refundRepo.findByTenant_TenantIdOrderByCreatedAtDesc(tenantId).stream().filter(com.scantoorder.scantoorder.data.model.Refund::isOpen).toList();
        floor.setOpenRefunds(open.size());
        floor.setOverdueRefunds(open.stream().filter(r -> r.getStatus() == com.scantoorder.scantoorder.data.model.RefundStatus.DUE
                && r.getCreatedAt().isBefore(now.minus(RefundTrackingServiceImpl.OVERDUE_AFTER))).count());
        for (Item item : itemRepo.findByTenant_TenantIdAndIsAvailableFalseOrderByItemNameAsc(tenantId)) {
            floor.getSoldOut().add(item.getItemName());
        }
        return floor;
    }
}
