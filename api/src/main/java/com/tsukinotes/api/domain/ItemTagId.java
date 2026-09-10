package com.tsukinotes.api.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ItemTagId implements Serializable {

    private Long itemId;
    private Long tagId;

    public ItemTagId() {
    }

    public ItemTagId(Long itemId, Long tagId) {
        this.itemId = itemId;
        this.tagId = tagId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemTagId)) return false;
        ItemTagId that = (ItemTagId) o;
        return Objects.equals(itemId, that.itemId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, tagId);
    }
}
