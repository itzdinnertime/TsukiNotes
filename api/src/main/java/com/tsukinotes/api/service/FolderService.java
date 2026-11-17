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
        String clean = validName(name);
        if (folderRepository.existsByIdentityIdAndNameIgnoreCase(identity.getId(), clean)) {
            throw nameTaken(clean);
        }

        Folder folder = new Folder();
        folder.setIdentityId(identity.getId());
        folder.setName(clean);
        folder.setCreatedAt(LocalDateTime.now());
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder renameFolder(Identity identity, Long folderId, String name) {
        Folder folder = findOwned(identity, folderId);
        String clean = validName(name);
        // Excluding this folder lets you change only the capitals, e.g. "cs 101" -> "CS 101".
        if (folderRepository.existsByIdentityIdAndNameIgnoreCaseAndIdNot(identity.getId(), clean, folderId)) {
            throw nameTaken(clean);
        }
        folder.setName(clean);
        return folder;
    }

    /**
     * Deletes the folder only; the items in it stay (the database removes the links).
     * Refused if it would leave an uploaded file in no folder, since uploads must always be in one.
     */
    @Transactional
    public void deleteFolder(Identity identity, Long folderId) {
        Folder folder = findOwned(identity, folderId);
        long stranded = folderRepository.countUploadsOnlyInFolder(folderId);
        if (stranded > 0) {
            String files = stranded == 1 ? "1 file is" : stranded + " files are";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    files + " only in \"" + folder.getName() + "\". Move "
                            + (stranded == 1 ? "it" : "them") + " to another folder or delete "
                            + (stranded == 1 ? "it" : "them") + " first.");
        }
        folderRepository.delete(folder);
    }

    /** Puts an item into folders (keeping any it's already in), after checking every folder belongs to this user. */
    @Transactional
    public void addItemToFolders(Identity identity, Long itemId, Collection<Long> folderIds) {
        for (Long folderId : ownedFolderIds(identity, folderIds)) {
            folderRepository.addItemToFolder(itemId, folderId);
        }
    }

    /** Makes the item's folders exactly this set. Caller must have checked the item belongs to the user. */
    @Transactional
    public void replaceItemFolders(Identity identity, Long itemId, Collection<Long> folderIds) {
        Set<Long> owned = ownedFolderIds(identity, folderIds);
        folderRepository.removeItemFromAllFolders(itemId);
        for (Long folderId : owned) {
            folderRepository.addItemToFolder(itemId, folderId);
        }
    }

    private Set<Long> ownedFolderIds(Identity identity, Collection<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> unique = new LinkedHashSet<>(folderIds);
        List<Folder> owned = folderRepository.findByIdInAndIdentityId(unique, identity.getId());
        if (owned.size() != unique.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the selected folders doesn't exist");
        }
        return unique;
    }

    private Folder findOwned(Identity identity, Long folderId) {
        return folderRepository.findByIdAndIdentityId(folderId, identity.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }

    private static String validName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a folder name");
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Folder names can be at most " + MAX_NAME_LENGTH + " characters");
        }
        return clean;
    }

    private static ResponseStatusException nameTaken(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "You already have a folder called \"" + name + "\"");
    }
}
