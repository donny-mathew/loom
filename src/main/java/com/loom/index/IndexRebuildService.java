package com.loom.index;

import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.WikilinkParser;
import com.loom.storage.ProjectPaths;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Wipes the SQLite index and rebuilds it by walking the markdown wiki tree.
 * The index must always be fully derivable from the markdown files — this
 * service is the proof of that invariant.
 */
@Service
public class IndexRebuildService {

    private final SqliteIndexStore store;
    private final ProjectPaths paths;
    private final FrontmatterParser frontmatterParser;
    private final WikilinkParser wikilinkParser;

    public IndexRebuildService(SqliteIndexStore store,
                               ProjectPaths paths,
                               FrontmatterParser frontmatterParser,
                               WikilinkParser wikilinkParser) {
        this.store = store;
        this.paths = paths;
        this.frontmatterParser = frontmatterParser;
        this.wikilinkParser = wikilinkParser;
    }

    @Transactional
    public void rebuild() {
        store.deleteAllFindings();
        store.deleteAllLinks();

        Path wikiDir = paths.wikiDir();
        if (!Files.exists(wikiDir)) {
            return;
        }

        try (var walk = Files.walk(wikiDir)) {
            walk.filter(p -> p.toString().endsWith(".md"))
                .filter(Files::isRegularFile)
                .forEach(this::indexPage);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void indexPage(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            FrontmatterParser.ParsedPage page = frontmatterParser.parse(raw);
            Map<String, Object> fm = page.frontmatter();

            String sessionId = getString(fm, "session-id", "");
            String type      = getString(fm, "type", "");
            String title     = getString(fm, "title", file.getFileName().toString().replace(".md", ""));
            String pagePath  = paths.wikiDir().relativize(file).toString();

            if (!sessionId.isBlank() && !type.isBlank()) {
                store.insertFinding(sessionId, type, title, page.body(), pagePath);
            }

            // Extract wikilinks and record as relates-to edges
            List<String> targets = wikilinkParser.extract(raw);
            for (String target : targets) {
                store.insertLink(pagePath, target, "relates-to");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getString(Map<String, Object> fm, String key, String defaultValue) {
        Object val = fm.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
