package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.dtos.request.CreateItemRequest;
import com.scantoorder.scantoorder.exception.CategoryCanNotBeFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ItemImplementation implements ItemService{
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private  ModelMapper modelMapper;
    @Override
    public String createItem(CreateItemRequest createitemRequest) {
        Optional<Category> category = Optional.of(categoryRepository.findCategoryByCategoryName(createitemRequest.getCategoryName()).orElseThrow(() -> new CategoryCanNotBeFoundException("category can not be found ")));
        try {
           Item item = modelMapper.map(createitemRequest, Item.class);
           item.setCategoryId(category.get());
           itemRepo.save(item);
            return "Item created successfully";
        }catch (DataIntegrityViolationException e){
            throw new RuntimeException("Item already exists");
        }
    }
}
