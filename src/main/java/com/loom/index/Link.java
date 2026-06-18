package com.loom.index;

public record Link(
        Long id,
        String sourcePath,
        String targetPath,
        String relationship,
        String createdAt
) {}
