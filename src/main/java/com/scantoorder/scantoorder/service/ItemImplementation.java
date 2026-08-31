package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ToggleItemResponse;
import com.scantoorder.scantoorder.exception.CategoryCanNotBeFoundException;
import com.scantoorder.scantoorder.exception.ItemNotFound;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ItemImplementation implements ItemService {
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Transactional
    @Override
    public CreateItemResponse createItem(CreateItemRequest createitemRequest) {
        Category category = categoryRepository.findCategoryByCategoryId((createitemRequest.getCategoryName())).orElseThrow(()-> new CategoryCanNotBeFoundException("category not found"));
        try {
            Item item = modelMapper.map(createitemRequest, Item.class);
            item.setCategoryId(category);
            item = itemRepo.save(item);
            redisTemplate.delete("menu:active");
            return new CreateItemResponse("Item created successfully", item.getItemId(), item.getItemName());
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Item already exists");
        }
    }

    @Transactional
    @Override
    public ToggleItemResponse toggleItem(String itemId) {
        Item item = itemRepo.findItemsByItemName(itemId)
                .orElseThrow(() -> new ItemNotFound("Item not found"));
        item.setAvailable(!item.isAvailable());
        item = itemRepo.save(item);
        redisTemplate.delete("menu:active");
        return new ToggleItemResponse("Item availability toggled", item.getItemId(), item.isAvailable());
    }

    @Transactional
    @Override
    public CreateItemResponse updateItem(String itemId, CreateItemRequest request) {
        Item item = itemRepo.findItemsByItemName(itemId)
                .orElseThrow(() -> new ItemNotFound("Item not found"));
        item.setItemName(request.getItemName());
        item.setItemDescription(request.getItemDescription());
        item.setItemPrice(BigDecimal.valueOf(request.getItemPrice()));
        item = itemRepo.save(item);
        redisTemplate.delete("menu:active");
        return new CreateItemResponse("Item updated successfully", item.getItemId(), item.getItemName());
    }

    @Transactional
    @Override
    public void deleteItem(String itemId) {
        Item item = itemRepo.findItemsByItemName(itemId)
                .orElseThrow(() -> new ItemNotFound("Item not found"));
        itemRepo.delete(item);
        redisTemplate.delete("menu:active");
    }
}
