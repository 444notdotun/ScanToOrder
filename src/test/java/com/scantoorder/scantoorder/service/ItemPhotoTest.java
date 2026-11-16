package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ItemPhotoTest {
    @Autowired
    private ItemService itemService;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private TenantTestSupport tenants;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private Category category;

    @BeforeEach
    void setUp() {
        tenants.signInAsManager();
        category = new Category();
        category.setTenant(tenants.main());
        category.setMenu(tenants.menuOf(tenants.main()));
        category.setCategoryName("Photos");
        categories.save(category);
    }

    @AfterEach
    void tearDown() {
        tenants.signOut();
    }

    private CreateItemRequest request(String name, String link) {
        CreateItemRequest request = new CreateItemRequest();
        request.setItemName(name);
        request.setItemDescription("A dish");
        request.setItemPrice(3000);
        request.setCategoryName(category.getCategoryName());
        request.setImageUrl(link);
        return request;
    }

    @Test
    void testThatAPhotoLinkIsSavedWithTheItemAndCanBeChangedOrRemoved() {
        String id = itemService.createItem(request("Photo plate", " https://example.com/a.jpg ")).getItemId();
        assertEquals("https://example.com/a.jpg", itemRepo.findById(id).orElseThrow().getImageUrl());

        itemService.updateItem(id, request("Photo plate", "https://example.com/b.jpg"));
        assertEquals("https://example.com/b.jpg", itemRepo.findById(id).orElseThrow().getImageUrl());

        itemService.updateItem(id, request("Photo plate", "  "));
        assertNull(itemRepo.findById(id).orElseThrow().getImageUrl());
    }

    @Test
    void testThatAnItemWithoutALinkHasNoPhoto() {
        Item made = itemRepo.findById(itemService.createItem(request("Plain plate", null)).getItemId()).orElseThrow();
        assertNull(made.getImageUrl());
    }

    @Test
    void testThatOnlyAWebLinkIsAcceptedAsAPhoto() {
        assertTrue(validator.validate(request("A", "https://example.com/a.jpg")).isEmpty());
        assertTrue(validator.validate(request("A", "")).isEmpty());
        assertTrue(validator.validate(request("A", null)).isEmpty());
        assertFalse(validator.validate(request("A", "javascript:alert(1)")).isEmpty());
        assertFalse(validator.validate(request("A", "not a link")).isEmpty());
        assertFalse(validator.validate(request("A", "https://example.com/" + "x".repeat(500))).isEmpty());
    }
}
