package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.MenuRepository;
import com.scantoorder.scantoorder.dtos.respond.CategoryAndItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ItemResponse;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MenuServiceImpl implements MenuService {
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String MENU_KEY_PREFIX = "menu:active:";

    @Override
    public MenuResponse generateMenu(String tenantId) {
        String key = MENU_KEY_PREFIX + tenantId;
        Object cachedMenu = redisTemplate.opsForValue().get(key);
        if (cachedMenu != null) {
            return (MenuResponse) cachedMenu;
        }
        List<Category> activeCategories = categoryRepository.findAllByTenant_TenantIdAndIsActiveTrue(tenantId);
        List<CategoryAndItemResponse> categoryAndItemResponseList = activeCategories.stream()
                .map(category -> {
                    CategoryAndItemResponse categoryResponse = new CategoryAndItemResponse();
                    categoryResponse.setCategoryName(category.getCategoryName());
                    List<Item> items = itemRepo.findAllByCategoryId(category);
                    List<ItemResponse> itemResponses = items.stream()
                            .map(item -> modelMapper.map(item, ItemResponse.class))
                            .toList();
                    categoryResponse.setItemResponse(itemResponses);
                    return categoryResponse;
                })
                .toList();
        MenuResponse menuResponse = new MenuResponse();
        menuResponse.setCategoryAndItemResponse(categoryAndItemResponseList);
        redisTemplate.opsForValue().set(key, menuResponse, 24, TimeUnit.HOURS);
        return menuResponse;
    }

    @Transactional
    @Override
    public Menu ensureActiveMenu(Tenant tenant) {
        return menuRepository.findActiveMenu(tenant.getTenantId()).orElseGet(() -> {
            Menu menu = new Menu();
            menu.setTenant(tenant);
            return menuRepository.save(menu);
        });
    }

    @Override
    public void clearCache(String tenantId) {
        redisTemplate.delete(MENU_KEY_PREFIX + tenantId);
    }
}
