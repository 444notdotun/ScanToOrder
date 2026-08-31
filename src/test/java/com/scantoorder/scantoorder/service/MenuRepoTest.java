package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.service.Interface.CategoryService;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import com.scantoorder.scantoorder.service.Interface.RestaurantTableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class MenuRepoTest {
    @Autowired
    private ItemService itemService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private RestaurantTableService tableService;

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    CreateCategoryRequest createCategoryRequest;
    CreateItemRequest createitemRequest;

    @BeforeEach
    void setUp() {
         createCategoryRequest = new CreateCategoryRequest();
        createCategoryRequest.setCategoryName("Testing");
        createitemRequest = new CreateItemRequest();
        createitemRequest.setItemDescription("Test item description");
        createitemRequest.setItemName("Test item name");
        createitemRequest.setItemPrice(7000);
        createitemRequest.setCategoryName(createCategoryRequest.getCategoryName());
        categoryService.createCategory(createCategoryRequest);
        itemService.createItem(createitemRequest);

    }
    @AfterEach
    public void tearDown() {
        itemRepo.deleteAll();
        categoryRepository.deleteAll();

    }

    @Test
    void testThatMenuCanBeGenerated(){
        MenuResponse menuResponse =tableService.generateMenu();
        assertNotNull(menuResponse);
        assertEquals(createCategoryRequest.getCategoryName(),menuResponse.getCategoryAndItemResponse().get(0).getCategoryName());
        assertEquals(createitemRequest.getItemDescription(),menuResponse.getCategoryAndItemResponse().get(0).getItemResponse().get(0).getItemDescription());
    }
}