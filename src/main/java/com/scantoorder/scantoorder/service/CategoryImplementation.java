package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.MenuRepository;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.respond.CategoryResponse;
import com.scantoorder.scantoorder.service.Interface.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryImplementation implements CategoryService {
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MenuRepository  menuRepository;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public String createCategory(CreateCategoryRequest createCategoryRequest) {
        try{
            Category category = CreateCategory(createCategoryRequest.getCategoryName());
            categoryRepository.save(category);
            redisTemplate.delete("menu:active");
            return category.getCategoryName()+" category created successfully";
        }catch (DataIntegrityViolationException e){
            throw new DataIntegrityViolationException("Duplicate key exception");
        }
    }

    private Category CreateCategory(String name) {
        Category category = new Category();
        category.setCategoryName(name);
        return category;
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByIsActiveTrue()
                .stream()
                .map(cat -> new CategoryResponse(cat.getCategoryId(), cat.getCategoryName(), cat.isActive()))
                .toList();
    }

    @Override
    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        categoryRepository.delete(category);
        redisTemplate.delete("menu:active");
    }

}
