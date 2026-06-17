package com.loom.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Ensures the wiki directory structure and seed files exist at startup.
 */
@Component
public class WikiInitializer implements ApplicationRunner {

    private final ProjectPaths paths;

    public WikiInitializer(ProjectPaths paths) {
        this.paths = paths;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Files.createDirectories(paths.rawDir());
            for (String dir : new String[]{"concepts", "decisions", "flows"}) {
                Files.createDirectories(paths.wikiTypeDir(dir));
            }
            createIfAbsent(paths.wikiIndex(), "# Index\n\nProject overview goes here.\n");
            createIfAbsent(paths.wikiLog(), "# Log\n\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void createIfAbsent(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }
}
