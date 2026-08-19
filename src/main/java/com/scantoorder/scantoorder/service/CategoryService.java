package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;

public interface CategoryService {

    String createCategory(CreateCategoryRequest createCategoryRequest);
}
