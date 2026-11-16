package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.respond.ToggleItemResponse;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The kitchen's stock switch: a sold-out item is not offered to guests and cannot be ordered.
 */
@Service
public class MenuStockService {

    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private MenuService menuService;

    @Transactional
    public ToggleItemResponse soldOut(String tenantId, String itemId) {
        Item item = set(tenantId, itemId, false);
        return new ToggleItemResponse(item.getItemName() + " is now sold out for guests.", item.getItemId(), false);
    }

    @Transactional
    public ToggleItemResponse backInStock(String tenantId, String itemId) {
        Item item = set(tenantId, itemId, true);
        return new ToggleItemResponse(item.getItemName() + " is back on the menu.", item.getItemId(), true);
    }

    private Item set(String tenantId, String itemId, boolean available) {
        Item item = itemRepo.findByItemIdAndTenant_TenantId(itemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that item"));
        item.setAvailable(available);
        itemRepo.save(item);
        menuService.clearCache(tenantId);
        return item;
    }
}
