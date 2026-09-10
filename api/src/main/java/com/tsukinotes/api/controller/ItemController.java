package com.tsukinotes.api.controller;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.dto.CreateItemRequest;
import com.tsukinotes.api.repository.IdentityRepository;
import com.tsukinotes.api.service.ItemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;
    private final IdentityRepository identityRepository;

    public ItemController(ItemService itemService, IdentityRepository identityRepository) {
        this.itemService = itemService;
        this.identityRepository = identityRepository;
    }

    @PostMapping
    public Item createItem(@RequestBody CreateItemRequest request) {
        Identity identity = identityRepository.findById(request.getIdentityId())
                .orElseThrow(() -> new IllegalArgumentException("Identity not found"));

        return itemService.createItem(identity, request.getUniqueKey(), request.getType(), request.getContent());
    }
}
