package com.tsukinotes.api.service;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.domain.ItemTag;
import com.tsukinotes.api.domain.Tag;
import com.tsukinotes.api.repository.ItemRepository;
import com.tsukinotes.api.repository.ItemTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemTagRepository itemTagRepository;
    private final TagService tagService;

    public ItemService(ItemRepository itemRepository, ItemTagRepository itemTagRepository, TagService tagService) {
        this.itemRepository = itemRepository;
        this.itemTagRepository = itemTagRepository;
        this.tagService = tagService;
    }

    @Transactional
    public Item createItem(Identity identity, String uniqueKey, String type, String content, List<String> tagNames) {
        if (itemRepository.existsByIdentityIdAndUniqueKey(identity.getId(), uniqueKey)) {
            throw new IllegalArgumentException("An item with this unique key already exists for this identity");
        }

        Item item = new Item();
        item.setIdentity(identity);
        item.setUniqueKey(uniqueKey);
        item.setType(type);
        item.setContent(content);
        item.setCreatedAt(java.time.LocalDateTime.now());
        item = itemRepository.save(item);

        for (String tagName : tagNames) {
            Tag tag = tagService.findOrCreate(tagName);
            ItemTag itemTag = new ItemTag();
            itemTag.setItem(item);
            itemTag.setTag(tag);
            itemTagRepository.save(itemTag);
        }

        return item;
    }
}
