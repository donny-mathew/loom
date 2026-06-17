package com.loom.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Append-only writes to raw/<date>-<topic>.md session files.
 * Raw files are never rewritten — only created and appended.
 */
@Component
public class RawSessionStore {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProjectPaths paths;

    public RawSessionStore(ProjectPaths paths) {
        this.paths = paths;
    }

    public Path create(String topic) {
        String date = LocalDate.now().format(DATE_FMT);
        String slug = topic.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Path file = paths.rawSession(date + "-" + slug + ".md");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                String header = "---\ntopic: " + topic + "\ndate: " + date + "\n---\n\n";
                Files.writeString(file, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void append(Path sessionFile, String line) {
        try {
            Files.writeString(sessionFile, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
