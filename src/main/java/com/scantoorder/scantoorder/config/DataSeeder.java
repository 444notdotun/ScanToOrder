package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedDatabase(CategoryRepository categoryRepository, ItemRepo itemRepo) {
        return args -> {
            if (categoryRepository.count() == 0 && itemRepo.count() == 0) {
                // Seed Categories
                Category appetizers = categoryRepository.save(Category.builder().categoryName("Appetizers & Small Chops").isActive(true).build());
                Category swallows = categoryRepository.save(Category.builder().categoryName("Gourmet Swallows & Native Soups").isActive(true).build());
                Category rice = categoryRepository.save(Category.builder().categoryName("Premium Rice & Specialties").isActive(true).build());
                Category grills = categoryRepository.save(Category.builder().categoryName("Grills & Prime Cuts").isActive(true).build());
                Category cocktails = categoryRepository.save(Category.builder().categoryName("Signature Cocktails & Cellar").isActive(true).build());

                // Seed Items for 'Appetizers & Small Chops'
                saveItem(itemRepo, "Executive Small Chops Platter", "Gourmet spring rolls, duck samosa, puff-puff with chili honey glaze, peppered gizzard", new BigDecimal("25000.00"), appetizers);
                saveItem(itemRepo, "Gourmet Asun Croquettes", "Slow-smoked goat meat tossed in habanero reduction inside crisp panko crust", new BigDecimal("22500.00"), appetizers);
                saveItem(itemRepo, "Spicy Jumbo Prawn Pepper Soup", "Atlantic tiger prawns in traditional aromatic uziza broth", new BigDecimal("35000.00"), appetizers);

                // Seed Items for 'Gourmet Swallows & Native Soups'
                saveItem(itemRepo, "Pounded Yam with Fisherman Seafood Okro", "Freshly pounded yam paired with jumbo crabs, calamari, lobster tail, and tiger prawns", new BigDecimal("65000.00"), swallows);
                saveItem(itemRepo, "Ijebu Garri Eba with Ofe Owerri Supreme", "Smoked snails, stockfish ear, goat tripe, and dried catfish", new BigDecimal("55000.00"), swallows);
                saveItem(itemRepo, "Amala Gourmet Trio (Ewedu, Gbegiri & Ogunfe)", "Silky abula accompanied by slow-braised tender goat shank and shaki", new BigDecimal("45000.00"), swallows);

                // Seed Items for 'Premium Rice & Specialties'
                saveItem(itemRepo, "Smoked Wagyu Party Jollof", "Woodsmoke firewood jollof served with sliced A5 Wagyu beef and sweet fried plantain coins", new BigDecimal("75000.00"), rice);
                saveItem(itemRepo, "Seafood Native Rice Extravaganza", "Local brown rice infused with dried prawns, lobster claws, periwinkle, and native spices", new BigDecimal("60000.00"), rice);
                saveItem(itemRepo, "Royal Ofada Platter", "Unpolished Ofada rice with rich ayamase green bleeder sauce, quail eggs, and shredded cow foot", new BigDecimal("48000.00"), rice);

                // Seed Items for 'Grills & Prime Cuts'
                saveItem(itemRepo, "Whole Grilled Atlantic Croaker / Point & Kill", "Charcoal-roasted fresh croaker drizzled in house yaji spice and sweet yam chips", new BigDecimal("52000.00"), grills);
                saveItem(itemRepo, "Suya-Spiced Tomahawk Steak", "800g prime bone-in ribeye crusted with roasted peanut yaji spice, served with truffled yam wedges", new BigDecimal("120000.00"), grills);
                saveItem(itemRepo, "Honey-Glazed Guinea Fowl", "Crisp roast local fowl tossed in ata rodo and local wild honey glaze", new BigDecimal("58000.00"), grills);

                // Seed Items for 'Signature Cocktails & Cellar'
                saveItem(itemRepo, "Smoked Palm Wine Elixir", "Distilled palm wine, clarified lime, mezcal, spiced hibiscus reduction", new BigDecimal("18000.00"), cocktails);
                saveItem(itemRepo, "Chapman Royale", "Classic Angostura, Campari, artisanal citrus blend, and passion fruit splash", new BigDecimal("15000.00"), cocktails);
                
                System.out.println("✅ Nigerian Fine-Dining Database Seeded Successfully!");
            }
        };
    }

    private void saveItem(ItemRepo itemRepo, String name, String description, BigDecimal price, Category category) {
        Item item = new Item();
        item.setItemName(name);
        item.setItemDescription(description);
        item.setItemPrice(price);
        item.setCategoryId(category);
        item.setAvailable(true);
        itemRepo.save(item);
    }
}
