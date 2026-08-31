package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateItemResponse;
import com.scantoorder.scantoorder.service.Interface.CategoryService;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ItemServiceTest {

    @Autowired
    private ItemService itemService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    private CreateItemRequest createitemRequest;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setCategoryName("test category");
        categoryRepository.save(category);
        createitemRequest = new CreateItemRequest();
        createitemRequest.setItemName("AMALA WITH EWEDU");
        createitemRequest.setItemPrice(20000);
        createitemRequest.setItemDescription("originated and owned by the yoruba, best in its league");
        createitemRequest.setCategoryName(category.getCategoryName());
    }
    @AfterEach
    void tearDown() {
        itemRepo.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void testThatItemCanBeCreated() {
        CreateItemResponse response = itemService.createItem(createitemRequest);
        assertNotNull(response);
        assertEquals("Item created successfully", response.getMessage());
    }

    @Test
    void testThatItemCanNotHaveTheSameNameTwice(){
        com.scantoorder.scantoorder.dtos.respond.CreateItemResponse response = itemService.createItem(createitemRequest);
        assertNotNull(response);
        assertEquals("Item created successfully", response.getMessage());
        assertThrows(RuntimeException.class,()->itemService.createItem(createitemRequest));


    }

}