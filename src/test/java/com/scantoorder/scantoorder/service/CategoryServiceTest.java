package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.MenuRepository;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.service.Interface.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;


    private CreateCategoryRequest  createCategoryRequest;

    @BeforeEach
    public void setUp() {
        itemRepo.deleteAll();
        categoryRepository.deleteAll();
        createCategoryRequest = new CreateCategoryRequest();
        createCategoryRequest.setCategoryName("Africa delicacys");
    }
    @AfterEach
    public void tearDown() {
        categoryRepository.deleteAll();
    }


    @Test
    void testThatCategoryCanBeCreated() {
        assertEquals(createCategoryRequest.getCategoryName()+" category created successfully",categoryService.createCategory(createCategoryRequest));
    }




    @Test
    void testThatCategoryWithTheSameNameCanBeCreatedTwice() {
        assertEquals(createCategoryRequest.getCategoryName()+" category created successfully",categoryService.createCategory(createCategoryRequest));
        assertThrows(DataIntegrityViolationException.class,()->categoryService.createCategory(createCategoryRequest));
    }

}