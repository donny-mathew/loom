package com.loom.storage;

import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.PageWriter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class WikiPageStore {

    private final ProjectPaths paths;
    private final FrontmatterParser frontmatterParser;
    private final PageWriter pageWriter;

    public WikiPageStore(ProjectPaths paths, FrontmatterParser frontmatterParser, PageWriter pageWriter) {
        this.paths = paths;
        this.frontmatterParser = frontmatterParser;
        this.pageWriter = pageWriter;
    }

    public Optional<FrontmatterParser.ParsedPage> read(String pageType, String filename) {
        Path file = paths.wikiPage(pageType, filename);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(frontmatterParser.parse(raw));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<FrontmatterParser.ParsedPage> readByPath(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(frontmatterParser.parse(raw));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(String pageType, String filename, Map<String, Object> frontmatter, String body) {
        Path file = paths.wikiPage(pageType, filename);
        ensureTypeDir(pageType);
        String content = frontmatterParser.write(frontmatter, body);
        pageWriter.write(file, content);
    }

    public void writeByPath(Path file, Map<String, Object> frontmatter, String body) {
        String content = frontmatterParser.write(frontmatter, body);
        pageWriter.write(file, content);
    }

    public void createIfAbsent(String pageType, String filename, String initialBody) {
        Path file = paths.wikiPage(pageType, filename);
        ensureTypeDir(pageType);
        if (!Files.exists(file)) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("title", filename.replace(".md", ""));
            pageWriter.write(file, frontmatterParser.write(fm, initialBody));
        }
    }

    private void ensureTypeDir(String pageType) {
        try {
            Files.createDirectories(paths.wikiTypeDir(pageType));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
