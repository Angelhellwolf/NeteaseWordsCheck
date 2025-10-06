package com.remiaft.neteasewordscheck.service;

import java.util.Objects;
import java.util.Optional;

public final class CheckResult {
    private final boolean allowed;
    private final String violationDetails;

    private CheckResult(boolean allowed, String violationDetails) {
        this.allowed = allowed;
        this.violationDetails = violationDetails;
    }

    public static CheckResult allowed() {
        return new CheckResult(true, null);
    }

    public static CheckResult denied(String violationDetails) {
        return new CheckResult(false, Objects.requireNonNull(violationDetails, "violationDetails"));
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Optional<String> getViolationDetails() {
        return Optional.ofNullable(violationDetails);
    }
}
