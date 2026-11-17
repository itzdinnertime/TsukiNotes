package com.tsukinotes.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Immutable
@Table(name = "item_search_view")
public class ItemSearchView {

    @Id
    private Long id;

    @Column(name = "identity_id")
    private Long identityId;

    @Column(name = "unique_key")
    private String uniqueKey;

    private String type;

    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private List<String> tags;

    // True for uploaded files; the file itself is fetched from GET /items/{id}/file.
    @Column(name = "has_file")
    private boolean hasFile;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    // getters only, no setters, this is read-only

    public Long getId() {
        return id;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean getHasFile() {
        return hasFile;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }
}
