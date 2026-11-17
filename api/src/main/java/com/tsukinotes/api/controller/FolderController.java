package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Folder;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.repository.FolderRepository.FolderSummary;
import com.tsukinotes.api.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /** The user's folders, alphabetically, each with its item count. */
    @GetMapping
    public List<FolderSummary> listFolders(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity) {
        return folderService.listFolders(identity);
    }

    @PostMapping
    public Folder createFolder(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                               @RequestBody CreateFolderRequest request) {
        return folderService.createFolder(identity, request.name());
    }

    /** Renames a folder. Body: { "name": "..." } */
    @PatchMapping("/{id}")
    public Folder renameFolder(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                               @PathVariable Long id,
                               @RequestBody CreateFolderRequest request) {
        return folderService.renameFolder(identity, id, request.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity,
                                             @PathVariable Long id) {
        folderService.deleteFolder(identity, id);
        return ResponseEntity.noContent().build();
    }

    public record CreateFolderRequest(String name) {
    }
}
