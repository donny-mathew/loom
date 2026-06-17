package com.loom.storage;

import com.loom.config.LoomConfig;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class ProjectPaths {

    private final Path projectRoot;

    public ProjectPaths(LoomConfig config) {
        this.projectRoot = config.projectRootPath();
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path rawDir() {
        return projectRoot.resolve("raw");
    }

    public Path wikiDir() {
        return projectRoot.resolve("wiki");
    }

    public Path wikiIndex() {
        return wikiDir().resolve("index.md");
    }

    public Path wikiLog() {
        return wikiDir().resolve("log.md");
    }

    public Path wikiTypeDir(String pageType) {
        return wikiDir().resolve(pageType);
    }

    public Path wikiPage(String pageType, String filename) {
        return wikiTypeDir(pageType).resolve(filename);
    }

    public Path rawSession(String filename) {
        return rawDir().resolve(filename);
    }
}
