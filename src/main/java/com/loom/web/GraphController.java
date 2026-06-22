package com.loom.web;

import com.loom.index.Finding;
import com.loom.index.Link;
import com.loom.index.SqliteIndexStore;
import com.loom.markdown.FrontmatterParser;
import com.loom.storage.ProjectPaths;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
public class GraphController {

    private final SqliteIndexStore indexStore;
    private final ProjectPaths paths;
    private final FrontmatterParser frontmatterParser;

    public GraphController(SqliteIndexStore indexStore, ProjectPaths paths, FrontmatterParser frontmatterParser) {
        this.indexStore = indexStore;
        this.paths = paths;
        this.frontmatterParser = frontmatterParser;
    }

    record GraphNode(String id, String title, String type, boolean converging) {}
    record GraphLink(String source, String target, String relationship) {}
    record GraphData(List<GraphNode> nodes, List<GraphLink> links) {}

    @GetMapping("/api/graph/data")
    public ResponseEntity<GraphData> graphData() {
        List<Finding> findings = indexStore.allFindings();
        List<Link> links = indexStore.allLinks();

        // Build nodes from findings — deduplicate by pagePath
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        for (Finding f : findings) {
            if (f.pagePath() == null) continue;
            boolean converging = isConverging(f.pagePath());
            nodeMap.put(f.pagePath(), new GraphNode(f.pagePath(), f.title(), f.type(), converging));
        }

        List<GraphLink> graphLinks = links.stream()
                .map(l -> new GraphLink(l.sourcePath(), l.targetPath(), l.relationship()))
                .toList();

        return ResponseEntity.ok(new GraphData(new ArrayList<>(nodeMap.values()), graphLinks));
    }

    private boolean isConverging(String pagePath) {
        try {
            Path file = paths.wikiDir().resolve(pagePath);
            if (!Files.exists(file)) return false;
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object val = frontmatterParser.parse(raw).frontmatter().get("converging");
            return Boolean.TRUE.equals(val);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
