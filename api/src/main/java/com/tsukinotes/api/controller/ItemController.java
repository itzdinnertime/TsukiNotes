package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.dto.CreateItemRequest;
import com.tsukinotes.api.service.ItemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public Item createItem(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                           @RequestBody CreateItemRequest request) {
        return itemService.createItem(identity, request.getUniqueKey(), request.getType(), request.getContent(), request.getTags());
    }
}
