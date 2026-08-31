package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.respond.CategoryAndItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ItemResponse;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
@Slf4j
@Service
public class MenuServiceImpl implements MenuService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepo itemRepo;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String MENU_KEY = "menu:active";

    @Override
    public MenuResponse generateMenu() {
        Object cachedMenu = redisTemplate.opsForValue().get(MENU_KEY);
        if (cachedMenu != null) {
            return (MenuResponse) cachedMenu;
        }
        List<Category> activeCategories = categoryRepository.findAllByIsActiveTrue();
        log.info("Active Categories: " + activeCategories.size());
        List<CategoryAndItemResponse> categoryAndItemResponseList = activeCategories.stream()
                .map(category -> {
                    CategoryAndItemResponse categoryResponse = new CategoryAndItemResponse();
                    categoryResponse.setCategoryName(category.getCategoryName());
                    log.info("Category: " + category.getCategoryName());
                    List<Item> items = itemRepo.findAllByCategoryId(category);
                    List<ItemResponse> itemResponses = items.stream()
                            .map(item -> modelMapper.map(item, ItemResponse.class))
                            .toList();
                            
                    categoryResponse.setItemResponse(itemResponses);
                    log.info("Items: " + itemResponses);
                    return categoryResponse;
                })
                .toList();
        MenuResponse menuResponse = new MenuResponse();
        menuResponse.setCategoryAndItemResponse(categoryAndItemResponseList);
System.out.println("Menu Response: " + menuResponse);
        redisTemplate.opsForValue().set(MENU_KEY, menuResponse, 24, TimeUnit.HOURS);
        return menuResponse;
    }
}
