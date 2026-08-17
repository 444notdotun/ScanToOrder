package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;

public interface ItemService {

    String createItem(CreateItemRequest createitemRequest);
}
