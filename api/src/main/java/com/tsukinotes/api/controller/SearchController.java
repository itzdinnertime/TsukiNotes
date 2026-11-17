package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.ItemSearchView;
import com.tsukinotes.api.repository.ItemSearchViewRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final ItemSearchViewRepository itemSearchViewRepository;

    public SearchController(ItemSearchViewRepository itemSearchViewRepository) {
        this.itemSearchViewRepository = itemSearchViewRepository;
    }

    @GetMapping
    public List<ItemSearchView> search(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                                       @RequestParam List<String> tags) {
        return itemSearchViewRepository.searchByTags(identity.getId(), tags.toArray(new String[0]));
    }
}
