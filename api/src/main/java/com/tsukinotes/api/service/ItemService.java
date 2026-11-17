package com.tsukinotes.api.service;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Item;
import com.tsukinotes.api.domain.ItemTag;
import com.tsukinotes.api.domain.Tag;
import com.tsukinotes.api.files.FileClassifier;
import com.tsukinotes.api.files.FileClassifier.FileKind;
import com.tsukinotes.api.files.FileStorageService;
import com.tsukinotes.api.repository.ItemRepository;
import com.tsukinotes.api.repository.ItemTagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ItemService {

    private static final int MAX_TITLE_LENGTH = 200;

    private final ItemRepository itemRepository;
    private final ItemTagRepository itemTagRepository;
    private final TagService tagService;
    private final FileStorageService fileStorageService;

    public ItemService(ItemRepository itemRepository, ItemTagRepository itemTagRepository,
                       TagService tagService, FileStorageService fileStorageService) {
        this.itemRepository = itemRepository;
        this.itemTagRepository = itemTagRepository;
        this.tagService = tagService;
        this.fileStorageService = fileStorageService;
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
        item.setCreatedAt(LocalDateTime.now());
        item = itemRepository.save(item);

        attachTags(item, tagNames);
        return item;
    }

    /**
     * Stores an uploaded image, text or code file as an item. It's tagged automatically with its kind
     * ("image", "text", or the language, plus "code" for code files), on top of any tags the user gave.
     */
    @Transactional
    public Item uploadFile(Identity identity, MultipartFile file, String title, List<String> extraTags) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file to upload");
        }

        String originalName = cleanFileName(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
        FileKind kind = FileClassifier.classify(originalName, bytes);

        String storedName = fileStorageService.save(bytes);
        // If anything below fails, the transaction rolls back; remove the file so it isn't orphaned.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    fileStorageService.delete(storedName);
                }
            }
        });

        Item item = new Item();
        item.setIdentity(identity);
        item.setUniqueKey(availableTitle(identity, title == null || title.isBlank() ? originalName : title.trim()));
        item.setType(kind.type());
        item.setContent(originalName);
        item.setCreatedAt(LocalDateTime.now());
        item.setStoredFileName(storedName);
        item.setOriginalFileName(originalName);
        item.setContentType(kind.contentType());
        item.setSizeBytes((long) bytes.length);
        item = itemRepository.save(item);

        List<String> tags = new ArrayList<>();
        if (kind.type().equals("code")) {
            tags.add("code");
        }
        tags.add(kind.tag());
        if (extraTags != null) {
            tags.addAll(extraTags);
        }
        attachTags(item, tags);
        return item;
    }

    /** Finds one of this user's uploaded files. Other users' items look like they don't exist. */
    public UploadedFile getFile(Identity identity, Long itemId) {
        Item item = itemRepository.findByIdAndIdentityId(itemId, identity.getId())
                .filter(i -> i.getStoredFileName() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        return new UploadedFile(fileStorageService.resolve(item.getStoredFileName()),
                item.getOriginalFileName(), item.getContentType());
    }

    public record UploadedFile(Path path, String originalFileName, String contentType) {
    }

    private void attachTags(Item item, List<String> tagNames) {
        if (tagNames == null) {
            return;
        }
        // De-duplicate so e.g. a user typing "python" on a .py file doesn't add the tag twice.
        Set<String> unique = new LinkedHashSet<>();
        for (String name : tagNames) {
            if (name != null && !name.isBlank()) {
                unique.add(name.trim());
            }
        }
        for (String name : unique) {
            Tag tag = tagService.findOrCreate(name);
            ItemTag itemTag = new ItemTag();
            itemTag.setItem(item);
            itemTag.setTag(tag);
            itemTagRepository.save(itemTag);
        }
    }

    /** Uses the title as-is if free, otherwise "title (2)", "title (3)", ... so re-uploading a file just works. */
    private String availableTitle(Identity identity, String title) {
        String base = title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
        String candidate = base;
        for (int n = 2; itemRepository.existsByIdentityIdAndUniqueKey(identity.getId(), candidate); n++) {
            candidate = base + " (" + n + ")";
        }
        return candidate;
    }

    private static String cleanFileName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        // Browsers may send a full path (old Edge/IE); keep only the last segment.
        String last = name.replace('\\', '/');
        last = last.substring(last.lastIndexOf('/') + 1).trim();
        if (last.length() > 255) {
            int dot = last.lastIndexOf('.');
            String ext = dot > 0 && last.length() - dot <= 16 ? last.substring(dot) : "";
            last = last.substring(0, 255 - ext.length()) + ext;
        }
        return last.isEmpty() ? "file" : last;
    }
}
