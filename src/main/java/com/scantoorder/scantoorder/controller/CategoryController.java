package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;
@GetMapping("/category")
    public ResponseEntity<ApiResponse<?>> createCategory(@RequestBody CreateCategoryRequest createCategoryRequest) {
    return   ResponseEntity.ok(new ApiResponse<>(categoryService.createCategory(createCategoryRequest)));
}

}
