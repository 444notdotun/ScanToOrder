package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.CreateCategoryRequest;
import com.scantoorder.scantoorder.dtos.respond.CategoryResponse;
import com.scantoorder.scantoorder.exception.CategoryCanNotBeFoundException;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.service.Interface.CategoryService;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryImplementation implements CategoryService {
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private MenuService menuService;

    @Transactional
    @Override
    public String createCategory(CreateCategoryRequest createCategoryRequest) {
        String tenantId = CurrentTenant.id();
        String name = createCategoryRequest.getCategoryName().trim();
        if (categoryRepository.findByTenant_TenantIdAndCategoryName(tenantId, name).isPresent()) {
            throw new ConflictException("You already have a section called " + name + ".");
        }
        Tenant tenant = tenantRepo.getReferenceById(tenantId);
        Category category = new Category();
        category.setCategoryName(name);
        category.setTenant(tenant);
        category.setMenu(menuService.ensureActiveMenu(tenant));
        categoryRepository.save(category);
        menuService.clearCache(tenantId);
        return category.getCategoryName() + " category created successfully";
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByTenant_TenantIdAndIsActiveTrue(CurrentTenant.id())
                .stream()
                .map(cat -> new CategoryResponse(cat.getCategoryId(), cat.getCategoryName(), cat.isActive()))
                .toList();
    }

    @Transactional
    @Override
    public void deleteCategory(String id) {
        String tenantId = CurrentTenant.id();
        Category category = categoryRepository.findByCategoryIdAndTenant_TenantId(id, tenantId)
                .orElseThrow(() -> new CategoryCanNotBeFoundException("We cannot find that section"));
        if (itemRepo.existsByCategoryId(category)) {
            throw new ConflictException("Take the plates out of this section first.");
        }
        categoryRepository.delete(category);
        menuService.clearCache(tenantId);
    }
}
