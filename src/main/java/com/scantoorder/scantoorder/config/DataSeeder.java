package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.MenuRepository;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@Order(2)
public class DataSeeder implements CommandLineRunner {

    public static final String CORNER_TABLE_SLUG = "thecornertable";
    public static final String MAMA_PUT_SLUG = "mamaputexpress";
    public static final String PENDING_SLUG = "sunsetgrill";
    public static final String MANAGER_USERNAME = CORNER_TABLE_SLUG + ".manager";
    public static final int[] TABLE_SIZES = {1, 2, 4, 6};

    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private MenuService menuService;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private RestaurantTableService tableService;

    @Value("${app.seed.manager-password}")
    private String managerPassword;
    @Value("${app.seed.demo-subaccount:}")
    private String demoSubaccount;
    @Value("${app.seed.platform-admin-username}")
    private String platformAdminUsername;
    @Value("${app.seed.platform-admin-password}")
    private String platformAdminPassword;

    @Override
    public void run(String... args) {
        seedPlatformAdmin();
        Tenant cornerTable = seedTenant("The Corner Table", CORNER_TABLE_SLUG, "Ada Okafor", "ada@thecornertable.example", "08011110001");
        seedTeam(cornerTable, true);
        seedCornerTableMenu(cornerTable);
        seedTables(cornerTable, TABLE_SIZES);

        Tenant mamaPut = seedTenant("Mama Put Express", MAMA_PUT_SLUG, "Ngozi Eze", "ngozi@mamaput.example", "08011110002");
        seedTeam(mamaPut, false);
        seedMamaPutMenu(mamaPut);
        seedTables(mamaPut, new int[]{3});

        seedPendingApplication();
    }

    private void seedPlatformAdmin() {
        if (workerRepo.findByUsername(platformAdminUsername).isPresent()) return;
        Worker admin = new Worker();
        admin.setUsername(platformAdminUsername);
        admin.setFullName("Plately platform admin");
        admin.setRole(WorkerRole.PLATFORM_ADMIN);
        admin.setPassword(passwordEncoder.encode(platformAdminPassword));
        workerRepo.save(admin);
        log.info("Seeded the platform admin");
    }

    private Tenant seedTenant(String name, String slug, String owner, String email, String phone) {
        return tenantRepo.findBySlug(slug).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setName(name);
            tenant.setSlug(slug);
            tenant.setType(TenantType.RESTAURANT);
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setOpenForOrders(true);
            tenant.setHoldsEnabled(true);
            tenant.setOwnerName(owner);
            tenant.setEmail(email);
            tenant.setPhone(phone);
            tenant.setBankCode("058");
            tenant.setBankName("Guaranty Trust Bank");
            tenant.setAccountNumber("0123456789");
            tenant.setAccountName(name.toUpperCase());
            tenant.setApprovedAt(LocalDateTime.now());
            // Without SEED_DEMO_SUBACCOUNT the demo restaurants cannot take payments.
            tenant.setPaystackSubaccountCode(demoSubaccount == null || demoSubaccount.isBlank() ? null : demoSubaccount);
            tenant = tenantRepo.save(tenant);
            menuOf(tenant);
            log.info("Seeded {}", name);
            return tenant;
        });
    }

    private void seedTeam(Tenant tenant, boolean withChefAndWaiter) {
        seedWorker(tenant, "manager", WorkerRole.MANAGER, tenant.getOwnerName());
        if (withChefAndWaiter) {
            seedWorker(tenant, "chef", WorkerRole.CHEF, "Demo Chef");
            seedWorker(tenant, "waiter", WorkerRole.WAITER, "Demo Waiter");
        }
    }

    // Demo accounts keep working: no forced password change.
    private void seedWorker(Tenant tenant, String handle, WorkerRole role, String fullName) {
        String username = tenant.getSlug() + "." + handle;
        if (workerRepo.existsByUsername(username)) return;
        Worker worker = new Worker();
        worker.setTenant(tenant);
        worker.setUsername(username);
        worker.setFullName(fullName);
        worker.setRole(role);
        worker.setPassword(passwordEncoder.encode(managerPassword));
        worker.setMustChangePassword(false);
        workerRepo.save(worker);
    }

    private void seedTables(Tenant tenant, int[] sizes) {
        if (!tableService.viewAllTable(tenant.getTenantId()).isEmpty()) return;
        for (int size : sizes) {
            tableService.createTable(tenant.getTenantId(), size);
        }
    }

    private void seedCornerTableMenu(Tenant tenant) {
        if (categoryRepository.findAllByTenant_TenantIdAndIsActiveTrue(tenant.getTenantId()).size() > 0) return;
        Category appetizers = saveCategory(tenant, "Appetizers & Small Chops");
        Category swallows = saveCategory(tenant, "Gourmet Swallows & Native Soups");
        Category rice = saveCategory(tenant, "Premium Rice & Specialties");
        Category grills = saveCategory(tenant, "Grills & Prime Cuts");
        Category cocktails = saveCategory(tenant, "Signature Cocktails & Cellar");

        saveItem(tenant, "Executive Small Chops Platter", "Gourmet spring rolls, duck samosa, puff-puff with chili honey glaze, peppered gizzard", "25000.00", appetizers);
        saveItem(tenant, "Gourmet Asun Croquettes", "Slow-smoked goat meat tossed in habanero reduction inside crisp panko crust", "22500.00", appetizers);
        saveItem(tenant, "Spicy Jumbo Prawn Pepper Soup", "Atlantic tiger prawns in traditional aromatic uziza broth", "35000.00", appetizers);

        saveItem(tenant, "Pounded Yam with Fisherman Seafood Okro", "Freshly pounded yam paired with jumbo crabs, calamari, lobster tail, and tiger prawns", "65000.00", swallows);
        saveItem(tenant, "Ijebu Garri Eba with Ofe Owerri Supreme", "Smoked snails, stockfish ear, goat tripe, and dried catfish", "55000.00", swallows);
        saveItem(tenant, "Amala Gourmet Trio (Ewedu, Gbegiri & Ogunfe)", "Silky abula accompanied by slow-braised tender goat shank and shaki", "45000.00", swallows);

        saveItem(tenant, "Smoked Wagyu Party Jollof", "Woodsmoke firewood jollof served with sliced A5 Wagyu beef and sweet fried plantain coins", "75000.00", rice);
        saveItem(tenant, "Seafood Native Rice Extravaganza", "Local brown rice infused with dried prawns, lobster claws, periwinkle, and native spices", "60000.00", rice);
        saveItem(tenant, "Royal Ofada Platter", "Unpolished Ofada rice with rich ayamase green bleeder sauce, quail eggs, and shredded cow foot", "48000.00", rice);

        saveItem(tenant, "Whole Grilled Atlantic Croaker / Point & Kill", "Charcoal-roasted fresh croaker drizzled in house yaji spice and sweet yam chips", "52000.00", grills);
        saveItem(tenant, "Suya-Spiced Tomahawk Steak", "800g prime bone-in ribeye crusted with roasted peanut yaji spice, served with truffled yam wedges", "120000.00", grills);
        saveItem(tenant, "Honey-Glazed Guinea Fowl", "Crisp roast local fowl tossed in ata rodo and local wild honey glaze", "58000.00", grills);

        saveItem(tenant, "Smoked Palm Wine Elixir", "Distilled palm wine, clarified lime, mezcal, spiced hibiscus reduction", "18000.00", cocktails);
        saveItem(tenant, "Chapman Royale", "Classic Angostura, Campari, artisanal citrus blend, and passion fruit splash", "15000.00", cocktails);
        menuService.clearCache(tenant.getTenantId());
    }

    private void seedMamaPutMenu(Tenant tenant) {
        if (categoryRepository.findAllByTenant_TenantIdAndIsActiveTrue(tenant.getTenantId()).size() > 0) return;
        Category mains = saveCategory(tenant, "Mains");
        Category drinks = saveCategory(tenant, "Drinks");
        saveItem(tenant, "Jollof Rice and Chicken", "Party jollof with a quarter chicken", "3500.00", mains);
        saveItem(tenant, "Pounded Yam and Egusi", "With assorted meat", "4500.00", mains);
        saveItem(tenant, "Chapman Royale", "The house chapman", "1500.00", drinks);
        menuService.clearCache(tenant.getTenantId());
    }

    private void seedPendingApplication() {
        if (tenantRepo.findBySlug(PENDING_SLUG).isPresent()) return;
        Tenant tenant = new Tenant();
        tenant.setName("Sunset Grill");
        tenant.setSlug(PENDING_SLUG);
        tenant.setType(TenantType.RESTAURANT);
        tenant.setStatus(TenantStatus.PENDING_APPROVAL);
        tenant.setHoldsEnabled(true);
        tenant.setOwnerName("Tunde Bello");
        tenant.setEmail("tunde@sunsetgrill.example");
        tenant.setPhone("08011110003");
        tenant.setBankCode("058");
        tenant.setBankName("Guaranty Trust Bank");
        tenant.setAccountNumber("0123456789");
        tenant.setAccountName("SUNSET GRILL LTD");
        tenantRepo.save(tenant);
        log.info("Seeded one application waiting for approval");
    }

    private Menu menuOf(Tenant tenant) {
        return menuRepository.findActiveMenu(tenant.getTenantId()).orElseGet(() -> {
            Menu menu = new Menu();
            menu.setTenant(tenant);
            return menuRepository.save(menu);
        });
    }

    private Category saveCategory(Tenant tenant, String name) {
        Menu menu = menuOf(tenant);
        Category category = new Category();
        category.setCategoryName(name);
        category.setTenant(tenant);
        category.setMenu(menu);
        return categoryRepository.save(category);
    }

    private void saveItem(Tenant tenant, String name, String description, String price, Category category) {
        Item item = new Item();
        item.setTenant(tenant);
        item.setItemName(name);
        item.setItemDescription(description);
        item.setItemPrice(new BigDecimal(price));
        item.setCategoryId(category);
        item.setAvailable(true);
        itemRepo.save(item);
    }
}
