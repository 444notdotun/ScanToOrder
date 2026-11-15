package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Order(2)
public class DataSeeder implements CommandLineRunner {

    public static final String MANAGER_USERNAME = "manager";
    public static final int[] TABLE_SIZES = {1, 2, 4, 6};

    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private RestaurantTableService tableService;

    @Value("${app.seed.manager-password}")
    private String managerPassword;

    @Override
    public void run(String... args) {
        seedManager();
        seedMenu();
        seedTables();
    }

    private void seedManager() {
        if (workerRepo.findByUsername(MANAGER_USERNAME).isPresent()) return;
        Worker manager = new Worker();
        manager.setPassword(passwordEncoder.encode(managerPassword));
        manager.setRole(WorkerRole.MANAGER);
        manager.setUsername(MANAGER_USERNAME);
        manager.setFullName("adewole dotun");
        workerRepo.save(manager);
        log.info("Seeded the manager account");
    }

    private void seedMenu() {
        if (categoryRepository.count() != 0 || itemRepo.count() != 0) return;
        Category appetizers = saveCategory("Appetizers & Small Chops");
        Category swallows = saveCategory("Gourmet Swallows & Native Soups");
        Category rice = saveCategory("Premium Rice & Specialties");
        Category grills = saveCategory("Grills & Prime Cuts");
        Category cocktails = saveCategory("Signature Cocktails & Cellar");

        saveItem("Executive Small Chops Platter", "Gourmet spring rolls, duck samosa, puff-puff with chili honey glaze, peppered gizzard", "25000.00", appetizers);
        saveItem("Gourmet Asun Croquettes", "Slow-smoked goat meat tossed in habanero reduction inside crisp panko crust", "22500.00", appetizers);
        saveItem("Spicy Jumbo Prawn Pepper Soup", "Atlantic tiger prawns in traditional aromatic uziza broth", "35000.00", appetizers);

        saveItem("Pounded Yam with Fisherman Seafood Okro", "Freshly pounded yam paired with jumbo crabs, calamari, lobster tail, and tiger prawns", "65000.00", swallows);
        saveItem("Ijebu Garri Eba with Ofe Owerri Supreme", "Smoked snails, stockfish ear, goat tripe, and dried catfish", "55000.00", swallows);
        saveItem("Amala Gourmet Trio (Ewedu, Gbegiri & Ogunfe)", "Silky abula accompanied by slow-braised tender goat shank and shaki", "45000.00", swallows);

        saveItem("Smoked Wagyu Party Jollof", "Woodsmoke firewood jollof served with sliced A5 Wagyu beef and sweet fried plantain coins", "75000.00", rice);
        saveItem("Seafood Native Rice Extravaganza", "Local brown rice infused with dried prawns, lobster claws, periwinkle, and native spices", "60000.00", rice);
        saveItem("Royal Ofada Platter", "Unpolished Ofada rice with rich ayamase green bleeder sauce, quail eggs, and shredded cow foot", "48000.00", rice);

        saveItem("Whole Grilled Atlantic Croaker / Point & Kill", "Charcoal-roasted fresh croaker drizzled in house yaji spice and sweet yam chips", "52000.00", grills);
        saveItem("Suya-Spiced Tomahawk Steak", "800g prime bone-in ribeye crusted with roasted peanut yaji spice, served with truffled yam wedges", "120000.00", grills);
        saveItem("Honey-Glazed Guinea Fowl", "Crisp roast local fowl tossed in ata rodo and local wild honey glaze", "58000.00", grills);

        saveItem("Smoked Palm Wine Elixir", "Distilled palm wine, clarified lime, mezcal, spiced hibiscus reduction", "18000.00", cocktails);
        saveItem("Chapman Royale", "Classic Angostura, Campari, artisanal citrus blend, and passion fruit splash", "15000.00", cocktails);

        redisTemplate.delete("menu:active");
        log.info("Seeded the sample menu");
    }

    private void seedTables() {
        if (tableRepo.count() != 0) return;
        for (int size : TABLE_SIZES) {
            tableService.createTable(size);
        }
        log.info("Seeded {} tables", TABLE_SIZES.length);
    }

    private Category saveCategory(String name) {
        return categoryRepository.save(Category.builder().categoryName(name).isActive(true).build());
    }

    private void saveItem(String name, String description, String price, Category category) {
        Item item = new Item();
        item.setItemName(name);
        item.setItemDescription(description);
        item.setItemPrice(new BigDecimal(price));
        item.setCategoryId(category);
        item.setAvailable(true);
        itemRepo.save(item);
    }
}
