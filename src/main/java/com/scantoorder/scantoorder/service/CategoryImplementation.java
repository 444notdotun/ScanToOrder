package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.MenuRepository;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.exception.MenuNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CategoryImplementation implements CategoryService{
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MenuRepository  menuRepository;
    @Override
    public String createCategory(CreateCategoryRequest createCategoryRequest) {
        try{
            Category category = CreateCategory(createCategoryRequest.getCategoryName());
            categoryRepository.save(category);
            return category.getCategoryName()+" category created successfully";
        }catch (DataIntegrityViolationException e){
            throw new DataIntegrityViolationException("Duplicate key exception");
        }
    }

    private Category CreateCategory(String name) {
        Category category = new Category();
        category.setCategoryName(name);
        categoryRepository.save(category);
        return category;
    }



}
