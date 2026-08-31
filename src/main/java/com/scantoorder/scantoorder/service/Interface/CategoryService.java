package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.respond.CategoryResponse;

import java.util.List;

public interface CategoryService {

    String createCategory(CreateCategoryRequest createCategoryRequest);

    List<CategoryResponse> getAllCategories();
    void deleteCategory(String id);
}
