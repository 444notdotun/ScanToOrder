package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.MenuRepository;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.exception.MenuNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private MenuRepository menuRepository;

    private CreateCategoryRequest  createCategoryRequest;

    @BeforeEach
    public void setUp() {
        createCategoryRequest = new CreateCategoryRequest();
        createCategoryRequest.setCategoryName("Africa delicacy");
    }
    @AfterEach
    public void tearDown() {
        categoryRepository.deleteAll();
    }

    @Test
    void testThatCategoryCanBeCreated() {
        Menu menu = new Menu();
        menuRepository.save(menu);
        assertEquals(createCategoryRequest.getCategoryName()+" category created successfully",categoryService.createCategory(createCategoryRequest));
    }

//    @Test
////    void testThatCategoryCanNotBeCreatedWithoutValidMenuId() {
////        String menuId = "menu67";
////        createCategoryRequest.setMenuId(menuId);
////        assertThrows(MenuNotFoundException.class, () -> categoryService.createCategory(createCategoryRequest));
////    }

    @Test
    void testThatCategoryWithTheSameNameCanBeCreatedTwice() {
        Menu menu = new Menu();
        menuRepository.save(menu);
        assertEquals(createCategoryRequest.getCategoryName()+" category created successfully",categoryService.createCategory(createCategoryRequest));
        assertThrows(DataIntegrityViolationException.class,()->categoryService.createCategory(createCategoryRequest));
    }

}