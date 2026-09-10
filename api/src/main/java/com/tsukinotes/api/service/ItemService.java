package com.tsukinotes.api.service;

import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional
    public Item createItem(Identity identity, String uniqueKey, String type, String content) {
        if (itemRepository.existsByIdentityIdAndUniqueKey(identity.getId(), uniqueKey)) {
            throw new IllegalArgumentException("An item with this unique key already exists for this identity");
        }

        Item item = new Item();
        item.setIdentity(identity);
        item.setUniqueKey(uniqueKey);
        item.setType(type);
        item.setContent(content);

        return itemRepository.save(item);
    }
}
