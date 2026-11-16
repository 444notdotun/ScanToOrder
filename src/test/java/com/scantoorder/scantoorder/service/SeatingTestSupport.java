package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.SeatClaimedResponse;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SeatingTestSupport {
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private EventGuestRepo eventGuestRepo;
    @Autowired
    private EventPlateRepo eventPlateRepo;
    @Autowired
    private EventPaymentRepo eventPaymentRepo;
    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ServiceCallRepo serviceCallRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private DinningSessionRepo dinningSessionRepo;
    @Autowired
    private SeatRepo seatRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TenantRepo tenantRepo;

    public String tenantId() {
        return tenants.mainId();
    }

    public Item saveItem(String name, int price, boolean available) {
        Item item = itemRepo.findByTenant_TenantIdAndItemName(tenants.mainId(), name).orElseGet(Item::new);
        item.setTenant(tenants.main());
        item.setItemName(name);
        item.setItemPrice(java.math.BigDecimal.valueOf(price));
        item = itemRepo.save(item);
        item.setAvailable(available);
        return itemRepo.save(item);
    }

    public void clearFloor() {
        paymentRepo.deleteAll();
        orderItemRepo.deleteAll();
        orderRepo.deleteAll();
        serviceCallRepo.deleteAll();
        // Event guests point at seats and seat memberships, so they go first.
        eventGuestRepo.deleteAll();
        sessionSeatRepo.deleteAll();
        dinningSessionRepo.deleteAll();
        seatRepo.deleteAll();
        tableRepo.deleteAll();
        eventPlateRepo.deleteAll();
        eventPaymentRepo.deleteAll();
        eventRepo.deleteAll();
        itemRepo.deleteAll();
        categoryRepository.deleteAll();
        tenants.resetMain();
    }

    public RestaurantTable newTable(int capacity) {
        return newTable(tenants.mainId(), capacity);
    }

    public RestaurantTable newTable(String tenantId, int capacity) {
        String tableId = tableService.createTable(tenantId, capacity).getTableId();
        return tableRepo.findById(tableId).orElseThrow();
    }

    public List<Seat> seatsOf(RestaurantTable table) {
        return seatRepo.findByTableIdOrderByPositionAsc(table);
    }

    public ClaimSeatRequest claimRequest(Seat seat, String name) {
        ClaimSeatRequest request = new ClaimSeatRequest();
        request.setSeatId(seat.getSeatId());
        request.setTableId(seat.getTableId().getTableNumber());
        request.setCustomerName(name);
        request.setCustomerPhoneNumber("08149048149");
        return request;
    }

    public CustomerPrincipal claim(Seat seat, String name) {
        SeatClaimedResponse response = seatService.claimSeat(claimRequest(seat, name), null);
        return principalFor(response.getSeatId());
    }

    public CustomerPrincipal principalFor(String seatId) {
        SessionSeat membership = membershipOf(seatId);
        return new CustomerPrincipal(membership.getSession().getSessionId(), seatId,
                membership.getSeat().getTableId().getTableId(), membership.getId(),
                membership.getSession().getTenant().getTenantId());
    }

    public com.scantoorder.scantoorder.data.model.DinningSession sessionOf(CustomerPrincipal guest) {
        return dinningSessionRepo.findById(guest.getSessionId()).orElseThrow();
    }

    public SessionSeat membershipOf(String seatId) {
        return sessionSeatRepo.findBySeatAndStatusIn(seatId, SeatLedger.ACTIVE).orElseThrow();
    }

    public void expireHold(String seatId) {
        SessionSeat membership = membershipOf(seatId);
        membership.setHeldUntil(LocalDateTime.now().minusSeconds(5));
        sessionSeatRepo.save(membership);
    }

    public Seat reload(Seat seat) {
        return seatRepo.findSeatBySeatId(seat.getSeatId()).orElseThrow();
    }
}
