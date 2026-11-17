package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.domain.ItemSearchView;
import com.tsukinotes.api.dto.CreateItemRequest;
import com.tsukinotes.api.repository.ItemSearchViewRepository;
import com.tsukinotes.api.service.ItemService;
import com.tsukinotes.api.service.ItemService.UploadedFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;
    private final ItemSearchViewRepository itemSearchViewRepository;

    public ItemController(ItemService itemService, ItemSearchViewRepository itemSearchViewRepository) {
        this.itemService = itemService;
        this.itemSearchViewRepository = itemSearchViewRepository;
    }

    /** All of the logged-in user's items, newest first. */
    @GetMapping
    public List<ItemSearchView> listItems(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity) {
        return itemSearchViewRepository.findByIdentityIdOrderByCreatedAtDesc(identity.getId());
    }

    @PostMapping
    public Item createItem(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                           @RequestBody CreateItemRequest request) {
        return itemService.createItem(identity, request.getUniqueKey(), request.getType(), request.getContent(), request.getTags());
    }

    /** Multipart upload: "file" (required), "title" and comma-separated "tags" (optional). */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Item uploadFile(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                           @RequestPart("file") MultipartFile file,
                           @RequestParam(value = "title", required = false) String title,
                           @RequestParam(value = "tags", required = false) String tags) {
        List<String> extraTags = tags == null ? List.of() : Arrays.asList(tags.split(","));
        return itemService.uploadFile(identity, file, title, extraTags);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                                                 @PathVariable Long id) {
        UploadedFile file = itemService.getFile(identity, id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Stop browsers from guessing a different type (e.g. treating a .txt as HTML).
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(new FileSystemResource(file.path()));
    }
}
