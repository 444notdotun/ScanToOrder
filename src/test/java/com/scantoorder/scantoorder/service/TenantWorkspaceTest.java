package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.CreateOrderRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;
import com.scantoorder.scantoorder.dtos.respond.ViewTableAndSeatAvailabilityResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.PlaceClosedException;
import com.scantoorder.scantoorder.exception.SeatStatusException;
import com.scantoorder.scantoorder.exception.SuspendedTenantException;
import com.scantoorder.scantoorder.service.Interface.CategoryService;
import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import com.scantoorder.scantoorder.service.Interface.SeatService;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TenantWorkspaceTest {
    private static final String CLOSED = "This place isn't taking orders on Plately yet.";

    @Autowired
    private TenantWorkspaceService workspace;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private RestaurantTableService tableService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private DiningSessionService sessionService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private SeatingTestSupport support;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private WebApplicationContext wac;

    private Tenant tenant;
    private String id;

    @BeforeEach
    void setUp() {
        tenant = tenants.freshActiveTenant();
        id = tenant.getTenantId();
    }

    @AfterEach
    void tearDown() {
        tenants.signOut();
    }

    private Item addItem(String name, boolean available) {
        Category category = categoryRepository.findByTenant_TenantIdAndCategoryName(id, "Mains").orElseGet(() -> {
            Category made = new Category();
            made.setTenant(tenant);
            made.setMenu(tenants.menuOf(tenant));
            made.setCategoryName("Mains");
            return categoryRepository.save(made);
        });
        Item item = new Item();
        item.setTenant(tenant);
        item.setItemName(name);
        item.setItemPrice(java.math.BigDecimal.valueOf(1000));
        item.setCategoryId(category);
        item = itemRepo.save(item);
        item.setAvailable(available);
        return itemRepo.save(item);
    }

    private RestaurantTable addTable(int seats) {
        return support.newTable(id, seats);
    }

    private ClaimSeatRequest claim(Seat seat) {
        return support.claimRequest(seat, "Guest");
    }

    @Test
    void testThatAManagerCannotOpenWithoutAnyPlateOrTable() {
        ConflictException both = assertThrows(ConflictException.class, () -> workspace.setOpenForOrders(id, true));
        assertEquals("You can open once you have at least one plate in stock on your menu and at least one table.", both.getMessage());

        addItem("Jollof", true);
        ConflictException noTable = assertThrows(ConflictException.class, () -> workspace.setOpenForOrders(id, true));
        assertEquals("You can open once you have at least one table.", noTable.getMessage());
        assertFalse(tenantRepo.findById(id).orElseThrow().isOpenForOrders());
    }

    @Test
    void testThatARestaurantWithoutASubaccountCannotOpenForOrders() {
        addItem("Jollof", true);
        addTable(2);
        tenant.setPaystackSubaccountCode(null);
        tenantRepo.save(tenant);

        ConflictException exception = assertThrows(ConflictException.class, () -> workspace.setOpenForOrders(id, true));

        assertEquals("We can't take payments for you yet. Contact us to finish your payout setup.", exception.getMessage());
        assertFalse(tenantRepo.findById(id).orElseThrow().isOpenForOrders());
        tenant.setPaystackSubaccountCode(TenantTestSupport.SUBACCOUNT);
        tenantRepo.save(tenant);
        assertTrue(workspace.setOpenForOrders(id, true).isOpenForOrders());
    }

    @Test
    void testThatAPlateThatIsOutOfStockDoesNotCountTowardsOpening() {
        addItem("Sold out", false);
        addTable(2);
        ConflictException exception = assertThrows(ConflictException.class, () -> workspace.setOpenForOrders(id, true));
        assertEquals("You can open once you have at least one plate in stock on your menu.", exception.getMessage());
    }

    @Test
    void testThatAManagerWithAMenuAndATableCanOpenAndCloseAgain() {
        addItem("Jollof", true);
        addTable(2);

        assertTrue(workspace.setOpenForOrders(id, true).isOpenForOrders());
        assertTrue(tenantRepo.findById(id).orElseThrow().isOpenForOrders());
        assertFalse(workspace.setOpenForOrders(id, false).isOpenForOrders());
        assertFalse(workspace.setOpenForOrders(id, false).isOpenForOrders());
    }

    @Test
    void testThatASuspendedBusinessCannotOpenButCanAlwaysClose() {
        addItem("Jollof", true);
        addTable(2);
        workspace.setOpenForOrders(id, true);
        tenant = tenantRepo.findById(id).orElseThrow();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepo.save(tenant);

        assertFalse(workspace.setOpenForOrders(id, false).isOpenForOrders());
        assertThrows(SuspendedTenantException.class, () -> workspace.setOpenForOrders(id, true));
    }

    @Test
    void testThatAnEventPlannerCannotOpenForOrdersOrTurnHoldsOnYet() {
        tenant.setType(TenantType.EVENT_PLANNER);
        tenant.setHoldsEnabled(false);
        tenantRepo.save(tenant);
        addItem("Plate", true);
        addTable(2);

        assertThrows(InvalidRequestException.class, () -> workspace.setOpenForOrders(id, true));
        assertThrows(InvalidRequestException.class, () -> workspace.setHolds(id, true));
        assertFalse(workspace.setHolds(id, false).isHoldsEnabled());
    }

    @Test
    void testThatTheHoldsSwitchControlsHoldingForThatBusinessOnly() {
        addItem("Jollof", true);
        RestaurantTable table = addTable(3);
        workspace.setOpenForOrders(id, true);
        List<Seat> seats = support.seatsOf(table);
        CustomerPrincipal host = support.claim(seats.get(0), "Host");

        assertEquals(1, sessionService.holdSeats(host, List.of(seats.get(1).getSeatId())).getSeats().stream().filter(s -> s.getStatus().equals("HELD")).count());

        workspace.setHolds(id, false);
        assertFalse(sessionService.getMySession(host).isHoldsEnabled());
        SeatStatusException exception = assertThrows(SeatStatusException.class,
                () -> sessionService.holdSeats(host, List.of(seats.get(2).getSeatId())));
        assertTrue(exception.getMessage().contains("switched off"));
        assertTrue(tenants.main().isHoldsEnabled());

        workspace.setHolds(id, true);
        assertEquals(2, sessionService.holdSeats(host, List.of(seats.get(2).getSeatId())).getSeats().stream().filter(s -> s.getStatus().equals("HELD")).count());
    }

    @Test
    void testThatAGuestCannotSitDownWhileTheBusinessIsClosedPendingDeclinedOrSuspended() {
        addItem("Jollof", true);
        Seat seat = support.seatsOf(addTable(2)).get(0);

        assertEquals(CLOSED, assertThrows(PlaceClosedException.class, () -> seatService.claimSeat(claim(seat), null)).getMessage());

        workspace.setOpenForOrders(id, true);
        for (TenantStatus status : List.of(TenantStatus.PENDING_APPROVAL, TenantStatus.DECLINED, TenantStatus.SUSPENDED)) {
            Tenant changed = tenantRepo.findById(id).orElseThrow();
            changed.setStatus(status);
            tenantRepo.save(changed);
            assertEquals(CLOSED, assertThrows(PlaceClosedException.class, () -> seatService.claimSeat(claim(seat), null)).getMessage(), status.name());
        }

        Tenant open = tenantRepo.findById(id).orElseThrow();
        open.setStatus(TenantStatus.ACTIVE);
        tenantRepo.save(open);
        assertNotNull(seatService.claimSeat(claim(seat), null).getToken());
    }

    @Test
    void testThatAScanSeesNothingWhileTheBusinessIsClosedAndAnUnknownSlugGetsTheSameAnswer() {
        addItem("Jollof", true);
        RestaurantTable table = addTable(2);

        assertEquals(CLOSED, assertThrows(PlaceClosedException.class, () -> tableService.publicSeatMap(tenant.getSlug(), table.getDisplayNumber())).getMessage());
        assertEquals(CLOSED, assertThrows(PlaceClosedException.class, () -> tableService.publicSeatMap("nosuchplace", 1)).getMessage());

        workspace.setOpenForOrders(id, true);
        ViewTableAndSeatAvailabilityResponse map = tableService.publicSeatMap(tenant.getSlug(), table.getDisplayNumber());
        assertEquals(2, map.getSeats().size());
        assertThrows(com.scantoorder.scantoorder.exception.TableNotFoundException.class, () -> tableService.publicSeatMap(tenant.getSlug(), 99));
    }

    @Test
    void testThatAGuestAlreadySeatedKeepsTheirSessionWhenTheBusinessClosesOrIsSuspended() {
        addItem("Jollof", true);
        RestaurantTable table = addTable(3);
        workspace.setOpenForOrders(id, true);
        List<Seat> seats = support.seatsOf(table);
        CustomerPrincipal host = support.claim(seats.get(0), "Host");
        String code = sessionService.holdSeats(host, List.of(seats.get(1).getSeatId())).getJoinCode();

        workspace.setOpenForOrders(id, false);
        Tenant suspended = tenantRepo.findById(id).orElseThrow();
        suspended.setStatus(TenantStatus.SUSPENDED);
        tenantRepo.save(suspended);

        CreateOrderRequest request = new CreateOrderRequest();
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName("Jollof");
        line.setQuantity(1);
        request.getOrderItems().add(line);
        assertNotNull(orderService.createOrder(request, host).getOrderId());
        assertNotNull(sessionService.getMySession(host).getSessionId());

        assertEquals(CLOSED, assertThrows(PlaceClosedException.class, () -> seatService.claimSeat(claim(seats.get(2)), null)).getMessage());
        JoinSeatRequest join = new JoinSeatRequest();
        join.setJoinCode(code);
        join.setCustomerName("Friend");
        join.setCustomerPhoneNumber("08012345678");
        assertEquals(CLOSED, assertThrows(PlaceClosedException.class, () -> seatService.joinSeat(seats.get(1).getSeatId(), join, null)).getMessage());

        assertEquals(seats.get(0).getSeatId(), seatService.claimSeat(claim(seats.get(2)), host).getSeatId());
    }

    @Test
    void testThatTheManagersOwnViewShowsOnlyTheLastFourDigitsOfTheAccount() {
        tenant.setBankName("Guaranty Trust Bank");
        tenant.setAccountNumber("0123456789");
        tenantRepo.save(tenant);

        MyTenantResponse mine = workspace.getMyTenant(id);

        assertEquals("6789", mine.getAccountLast4());
        assertEquals("Guaranty Trust Bank", mine.getBankName());
        assertEquals(tenant.getSlug(), mine.getSlug());
        assertTrue(java.util.Arrays.stream(MyTenantResponse.class.getDeclaredFields()).noneMatch(f -> f.getName().equals("accountNumber")));
    }

    @Test
    void testThatASectionWithPlatesInItCannotBeRemoved() {
        tenants.signInAs(com.scantoorder.scantoorder.data.model.WorkerRole.MANAGER, tenant);
        CreateCategoryRequest category = new CreateCategoryRequest();
        category.setCategoryName("Soups");
        categoryService.createCategory(category);
        CreateItemRequest item = new CreateItemRequest();
        item.setItemName("Egusi");
        item.setItemDescription("With goat meat");
        item.setCategoryName("Soups");
        item.setItemPrice(3000);
        itemService.createItem(item);
        String categoryId = categoryRepository.findByTenant_TenantIdAndCategoryName(id, "Soups").orElseThrow().getCategoryId();

        ConflictException exception = assertThrows(ConflictException.class, () -> categoryService.deleteCategory(categoryId));
        assertEquals("Take the plates out of this section first.", exception.getMessage());
        itemService.deleteItem("Egusi");
        categoryService.deleteCategory(categoryId);
        assertTrue(categoryRepository.findById(categoryId).isEmpty());
    }

    @Test
    void testThatSectionAndPlateNamesAreUniqueWithinABusinessOnly() {
        tenants.signInAs(com.scantoorder.scantoorder.data.model.WorkerRole.MANAGER, tenant);
        CreateCategoryRequest category = new CreateCategoryRequest();
        category.setCategoryName("Starters");
        categoryService.createCategory(category);
        assertThrows(ConflictException.class, () -> categoryService.createCategory(category));
        CreateItemRequest item = new CreateItemRequest();
        item.setItemName("Puff puff");
        item.setItemDescription("Sweet");
        item.setCategoryName("Starters");
        item.setItemPrice(500);
        itemService.createItem(item);
        assertThrows(ConflictException.class, () -> itemService.createItem(item));

        Tenant second = tenants.freshActiveTenant();
        tenants.signInAs(com.scantoorder.scantoorder.data.model.WorkerRole.MANAGER, second);
        categoryService.createCategory(category);
        assertEquals("Item created successfully", itemService.createItem(item).getMessage());
    }

    @Test
    void testThatEachBusinessHasItsOwnMenuAndItsOwnCacheKey() {
        addItem("Only Here", true);
        Tenant other = tenants.freshActiveTenant();

        MenuResponse mine = menuService.generateMenu(id);
        MenuResponse theirs = menuService.generateMenu(other.getTenantId());

        assertTrue(mine.getCategoryAndItemResponse().stream().flatMap(c -> c.getItemResponse().stream()).anyMatch(i -> i.getItemName().equals("Only Here")));
        assertTrue(theirs.getCategoryAndItemResponse().isEmpty());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("menu:active:" + id)));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("menu:active:" + other.getTenantId())));
        menuService.clearCache(id);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey("menu:active:" + id)));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("menu:active:" + other.getTenantId())));
    }

    private int statusFor(String method, String path, String role, String body) throws Exception {
        tenants.signInAs(com.scantoorder.scantoorder.data.model.WorkerRole.valueOf(role), tenant);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void testThatOnlyTheManagerCanUseTheWorkspaceSwitches() throws Exception {
        for (String role : List.of("CHEF", "WAITER")) {
            assertEquals(403, statusFor("PATCH", "/api/v1/tenant/open-for-orders", role, "true"), role);
            assertEquals(403, statusFor("PATCH", "/api/v1/tenant/holds", role, "false"), role);
            assertEquals(403, statusFor("GET", "/api/v1/tenant", role, null), role);
            assertEquals(403, statusFor("POST", "/api/v1/tables", role, "{\"seatCapacity\":2}"), role);
            assertEquals(403, statusFor("POST", "/api/v1/categories", role, "{\"categoryName\":\"x\"}"), role);
        }
        assertEquals(200, statusFor("PATCH", "/api/v1/tenant/holds", "MANAGER", "false"));
        assertEquals(409, statusFor("PATCH", "/api/v1/tenant/open-for-orders", "MANAGER", "true"));
        assertFalse(tenantRepo.findById(id).orElseThrow().isHoldsEnabled());
    }
}
