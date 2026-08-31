package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.dtos.respond.CreateItemResponse;
import com.scantoorder.scantoorder.dtos.respond.ToggleItemResponse;

public interface ItemService {
    CreateItemResponse createItem(CreateItemRequest createitemRequest);
    ToggleItemResponse toggleItem(String itemId);
    CreateItemResponse updateItem(String itemId, CreateItemRequest request);
    void deleteItem(String itemId);
}
