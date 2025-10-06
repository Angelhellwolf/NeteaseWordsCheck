package com.remiaft.neteasewordscheck.cache;

import java.time.Instant;
import java.util.Objects;

public final class CacheEntry {
    private final String content;
    private final String violation;
    private final Instant checkedAt;

    public CacheEntry(String content, String violation, Instant checkedAt) {
        this.content = Objects.requireNonNull(content, "content");
        this.violation = violation;
        this.checkedAt = Objects.requireNonNull(checkedAt, "checkedAt");
    }

    public String getContent() {
        return content;
    }

    public String getViolation() {
        return violation;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
