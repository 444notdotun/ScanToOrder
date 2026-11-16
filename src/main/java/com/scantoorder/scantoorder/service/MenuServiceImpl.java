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
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MenuServiceImpl implements MenuService {
    @org.springframework.beans.factory.annotation.Autowired
    private LiveUpdates liveUpdates;

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private ReadyTimeService readyTimeService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String MENU_KEY_PREFIX = "menu:active:";

    @Override
    public MenuResponse generateMenu(String tenantId) {
        String key = MENU_KEY_PREFIX + tenantId;
        Object cachedMenu = redisTemplate.opsForValue().get(key);
        if (cachedMenu != null) {
            return withReadyTimes(tenantId, (MenuResponse) cachedMenu);
        }
        List<Category> activeCategories = categoryRepository.findAllByTenant_TenantIdAndIsActiveTrue(tenantId);
        List<CategoryAndItemResponse> categoryAndItemResponseList = activeCategories.stream()
                .map(category -> {
                    CategoryAndItemResponse categoryResponse = new CategoryAndItemResponse();
                    categoryResponse.setCategoryName(category.getCategoryName());
                    // An item that is switched off is hidden from guests, not deleted.
                    List<Item> items = itemRepo.findAllByCategoryIdAndIsAvailableTrue(category);
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
        return withReadyTimes(tenantId, menuResponse);
    }

    // Ready times are worked out fresh each time and never stored with the cached menu, so they cannot go stale.
    private MenuResponse withReadyTimes(String tenantId, MenuResponse menu) {
        Map<String, Integer> times = readyTimeService.byItemName(tenantId);
        if (times.isEmpty()) return menu;
        for (CategoryAndItemResponse section : menu.getCategoryAndItemResponse()) {
            for (ItemResponse item : section.getItemResponse()) {
                item.setReadyInMinutes(times.get(item.getItemName()));
            }
        }
        return menu;
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
        // Every change to the menu comes through here, so every open screen is told to fetch it again.
        liveUpdates.publish(tenantId, "menu", null, null);
    }
}
