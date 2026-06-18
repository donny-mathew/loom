package com.loom.index;

public record Finding(
        Long id,
        String sessionId,
        String type,
        String title,
        String body,
        String pagePath,
        String createdAt
) {}
