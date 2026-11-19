package com.tsukinotes.api.files;

import com.tsukinotes.api.repository.ItemRepository;
import com.tsukinotes.api.repository.ItemRepository.UploadMissingText;
import com.tsukinotes.api.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * On startup, stores the text of any text/code uploads that don't have it yet (files uploaded
 * before searching inside files existed), so they show up in searches too. Does nothing once
 * every file has been done.
 */
@Component
public class ItemTextBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ItemTextBackfill.class);

    private final ItemRepository itemRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactions;

    public ItemTextBackfill(ItemRepository itemRepository, FileStorageService fileStorageService,
                            TransactionTemplate transactions) {
        this.itemRepository = itemRepository;
        this.fileStorageService = fileStorageService;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<UploadMissingText> missing = itemRepository.findUploadsMissingText();
        int done = 0;
        for (UploadMissingText upload : missing) {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(fileStorageService.resolve(upload.getStoredFileName()));
            } catch (IOException | RuntimeException e) {
                log.warn("Couldn't read the file for item {} to make it searchable: {}", upload.getId(), e.toString());
                continue;
            }
            // One small transaction per file, so one unreadable file doesn't undo the rest.
            transactions.executeWithoutResult(status ->
                    itemRepository.saveText(upload.getId(), ItemService.searchableText(bytes)));
            done++;
        }
        if (done > 0) {
            log.info("Made the text inside {} previously uploaded file(s) searchable", done);
        }
    }
}
