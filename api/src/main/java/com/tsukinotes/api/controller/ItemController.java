package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.domain.ItemSearchView;
import com.tsukinotes.api.dto.CreateItemRequest;
import com.tsukinotes.api.repository.ItemRepository;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;
    private final ItemSearchViewRepository itemSearchViewRepository;
    private final ItemRepository itemRepository;

    public ItemController(ItemService itemService, ItemSearchViewRepository itemSearchViewRepository,
                          ItemRepository itemRepository) {
        this.itemService = itemService;
        this.itemSearchViewRepository = itemSearchViewRepository;
        this.itemRepository = itemRepository;
    }

    /** Characters of file text shown on each side of a match. */
    private static final int SNIPPET_CONTEXT = 60;

    /**
     * The logged-in user's items, newest first.
     * Optional: "q" matches title, filename, tag or the text inside uploaded text/code files
     * (those results include "textMatch", an extract around the match); "folderId" limits to one folder.
     */
    @GetMapping
    public List<ItemSearchView> listItems(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                                          @RequestParam(value = "q", required = false) String query,
                                          @RequestParam(value = "folderId", required = false) Long folderId) {
        String cleanQuery = query == null ? "" : query.trim();
        List<ItemSearchView> items = itemSearchViewRepository.findForUser(
                identity.getId(), folderId == null ? 0 : folderId, cleanQuery, containsPattern(cleanQuery));
        if (!cleanQuery.isEmpty()) {
            addTextMatches(items, cleanQuery);
        }
        return items;
    }

    private void addTextMatches(List<ItemSearchView> items, String query) {
        Map<Long, ItemSearchView> textItems = items.stream()
                .filter(item -> item.getHasFile() && ItemService.hasSearchableText(item.getType()))
                .collect(Collectors.toMap(ItemSearchView::getId, item -> item));
        if (textItems.isEmpty()) {
            return;
        }
        for (ItemRepository.TextMatch match : itemRepository.findTextMatches(textItems.keySet(), query, SNIPPET_CONTEXT)) {
            textItems.get(match.getItemId()).setTextMatch(match.getSnippet());
        }
    }

    /** A LIKE pattern matching text that contains the query literally (its \, % and _ escaped). */
    static String containsPattern(String query) {
        return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    @PostMapping
    public Item createItem(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                           @RequestBody CreateItemRequest request) {
        return itemService.createItem(identity, request.getUniqueKey(), request.getType(), request.getContent(), request.getTags());
    }

    /**
     * Multipart upload: "file" and at least one "folderIds" (repeat the field, or comma-separate);
     * optional "title" and comma-separated "tags". The folder parameter isn't marked required here
     * so that a missing one gets the service's clear "must be put in at least one folder" message.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Item uploadFile(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                           @RequestPart("file") MultipartFile file,
                           @RequestParam(value = "title", required = false) String title,
                           @RequestParam(value = "tags", required = false) String tags,
                           @RequestParam(value = "folderIds", required = false) List<Long> folderIds) {
        List<String> extraTags = tags == null ? List.of() : Arrays.asList(tags.split(","));
        return itemService.uploadFile(identity, file, title, extraTags, folderIds == null ? List.of() : folderIds);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                                           @PathVariable Long id) {
        itemService.deleteItem(identity, id);
        return ResponseEntity.noContent().build();
    }

    /** Replaces the item's folders with exactly these (an empty list takes it out of every folder). */
    @PutMapping("/{id}/folders")
    public ResponseEntity<Void> setFolders(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                                           @PathVariable Long id,
                                           @RequestBody SetFoldersRequest request) {
        itemService.setItemFolders(identity, id, request.folderIds() == null ? List.of() : request.folderIds());
        return ResponseEntity.noContent().build();
    }

    public record SetFoldersRequest(List<Long> folderIds) {
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
