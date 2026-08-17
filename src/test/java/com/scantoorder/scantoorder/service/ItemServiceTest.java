package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
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

    @Autowired
    private CategoryService categoryService;
    private  CreateCategoryRequest  createCategoryRequest;
    private CreateItemRequest createitemRequest;

    @BeforeEach
    void setUp() {
        createCategoryRequest = new CreateCategoryRequest();
        createCategoryRequest.setCategoryName("African Meals");
        categoryService.createCategory(createCategoryRequest);
        createitemRequest = new CreateItemRequest();
        createitemRequest.setItemName("AMALA WITH EWEDU");
        createitemRequest.setItemPrice(20000);
        createitemRequest.setItemDescription("originated and owned by the yoruba, best in its league");
        createitemRequest.setCategoryName(createCategoryRequest.getCategoryName());
    }
    @AfterEach
    void tearDown() {
        itemRepo.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void testThatItemCanBeCreated() {
        Optional<Category> category = categoryRepository.findCategoryByCategoryName(createCategoryRequest.getCategoryName());
        assertNotNull(category.get());
        String response = itemService.createItem(createitemRequest);
        assertNotNull(response);
        assertEquals("Item created successfully", response);
    }

    @Test
    void testThatItemCanNotHaveTheSameNameTwice(){
        Optional<Category> category = categoryRepository.findCategoryByCategoryName(createCategoryRequest.getCategoryName());
        assertNotNull(category.get());
        String response = itemService.createItem(createitemRequest);
        assertNotNull(response);
        assertEquals("Item created successfully", response);
        assertThrows(RuntimeException.class,()->itemService.createItem(createitemRequest));


    }

}