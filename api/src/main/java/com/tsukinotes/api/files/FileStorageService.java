package com.tsukinotes.api.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stores uploaded files on disk under random names, so user-supplied filenames never touch the filesystem. */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload directory " + root, e);
        }
    }

    /** Saves the bytes and returns the generated name they were stored under. */
    public String save(byte[] bytes) {
        String storedName = UUID.randomUUID().toString();
        try {
            Files.write(resolve(storedName), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save uploaded file", e);
        }
        return storedName;
    }

    public Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Invalid stored file name");
        }
        return path;
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            // Best effort: an orphaned file is harmless.
        }
    }
}
