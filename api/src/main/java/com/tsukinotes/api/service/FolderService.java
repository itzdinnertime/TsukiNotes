package com.tsukinotes.api.service;

import com.tsukinotes.api.domain.Folder;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.repository.FolderRepository;
import com.tsukinotes.api.repository.FolderRepository.FolderSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FolderService {

    private static final int MAX_NAME_LENGTH = 100;

    private final FolderRepository folderRepository;

    public FolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    public List<FolderSummary> listFolders(Identity identity) {
        return folderRepository.findSummaries(identity.getId());
    }

    public Folder createFolder(Identity identity, String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a folder name");
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Folder names can be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (folderRepository.existsByIdentityIdAndNameIgnoreCase(identity.getId(), clean)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a folder called \"" + clean + "\"");
        }

        Folder folder = new Folder();
        folder.setIdentityId(identity.getId());
        folder.setName(clean);
        folder.setCreatedAt(LocalDateTime.now());
        return folderRepository.save(folder);
    }

    /** Deletes the folder only; the items in it stay (the database removes the links). */
    @Transactional
    public void deleteFolder(Identity identity, Long folderId) {
        Folder folder = folderRepository.findByIdAndIdentityId(folderId, identity.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        folderRepository.delete(folder);
    }

    /** Puts an item into folders, after checking every folder belongs to this user. */
    @Transactional
    public void addItemToFolders(Identity identity, Long itemId, Collection<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return;
        }
        Set<Long> unique = new LinkedHashSet<>(folderIds);
        List<Folder> owned = folderRepository.findByIdInAndIdentityId(unique, identity.getId());
        if (owned.size() != unique.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the selected folders doesn't exist");
        }
        for (Long folderId : unique) {
            folderRepository.addItemToFolder(itemId, folderId);
        }
    }
}
