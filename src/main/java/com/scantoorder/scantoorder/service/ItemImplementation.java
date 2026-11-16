package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ToggleItemResponse;
import com.scantoorder.scantoorder.exception.CategoryCanNotBeFoundException;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.ItemNotFound;
import com.scantoorder.scantoorder.service.Interface.ItemService;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
    private TenantRepo tenantRepo;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private MenuService menuService;
    @Autowired
    private OrderItemRepo orderItemRepo;

    @Transactional
    @Override
    public CreateItemResponse createItem(CreateItemRequest createitemRequest) {
        String tenantId = CurrentTenant.id();
        Category category = categoryRepository.findByTenant_TenantIdAndCategoryName(tenantId, createitemRequest.getCategoryName())
                .orElseThrow(() -> new CategoryCanNotBeFoundException("We cannot find that section. Create it first."));
        requireFreeName(tenantId, createitemRequest.getItemName(), null);
        Item item = new Item();
        item.setItemName(createitemRequest.getItemName().trim());
        item.setItemDescription(createitemRequest.getItemDescription());
        item.setItemPrice(BigDecimal.valueOf(createitemRequest.getItemPrice()));
        item.setImageUrl(photo(createitemRequest.getImageUrl()));
        item.setCategoryId(category);
        item.setTenant(tenantRepo.getReferenceById(tenantId));
        item = itemRepo.save(item);
        menuService.clearCache(tenantId);
        return new CreateItemResponse("Item created successfully", item.getItemId(), item.getItemName());
    }

    @Transactional
    @Override
    public ToggleItemResponse toggleItem(String itemId) {
        String tenantId = CurrentTenant.id();
        Item item = find(tenantId, itemId);
        item.setAvailable(!item.isAvailable());
        item = itemRepo.save(item);
        menuService.clearCache(tenantId);
        return new ToggleItemResponse("Item availability toggled", item.getItemId(), item.isAvailable());
    }

    @Transactional
    @Override
    public CreateItemResponse updateItem(String itemId, CreateItemRequest request) {
        String tenantId = CurrentTenant.id();
        Item item = find(tenantId, itemId);
        requireFreeName(tenantId, request.getItemName(), item.getItemId());
        item.setItemName(request.getItemName().trim());
        item.setItemDescription(request.getItemDescription());
        item.setItemPrice(BigDecimal.valueOf(request.getItemPrice()));
        item.setImageUrl(photo(request.getImageUrl()));
        item = itemRepo.save(item);
        menuService.clearCache(tenantId);
        return new CreateItemResponse("Item updated successfully", item.getItemId(), item.getItemName());
    }

    @Transactional
    @Override
    public void deleteItem(String itemId) {
        String tenantId = CurrentTenant.id();
        Item item = find(tenantId, itemId);
        // An item that has been ordered is part of the record: it can be switched off, never deleted.
        if (orderItemRepo.existsByItem_ItemId(item.getItemId())) {
            throw new ConflictException(EventPlateService.ORDERED_BEFORE);
        }
        itemRepo.delete(item);
        menuService.clearCache(tenantId);
    }

    private String photo(String link) {
        return link == null || link.isBlank() ? null : link.trim();
    }

    // The path carries an item's id or, as the existing screens send it, its name.
    private Item find(String tenantId, String idOrName) {
        return itemRepo.findByItemIdAndTenant_TenantId(idOrName, tenantId)
                .or(() -> itemRepo.findByTenant_TenantIdAndItemName(tenantId, idOrName))
                .orElseThrow(() -> new ItemNotFound("We cannot find that plate"));
    }

    private void requireFreeName(String tenantId, String name, String ownId) {
        itemRepo.findByTenant_TenantIdAndItemName(tenantId, name.trim()).ifPresent(existing -> {
            if (!existing.getItemId().equals(ownId)) {
                throw new ConflictException("There is already a plate called " + name.trim() + " on your menu.");
            }
        });
    }
}
