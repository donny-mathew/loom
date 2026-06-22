package com.loom.web;

import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.PageWriter;
import com.loom.storage.ProjectPaths;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Controller
public class WikiController {

    private final ProjectPaths paths;
    private final FrontmatterParser frontmatterParser;
    private final PageWriter pageWriter;
    private final Parser mdParser;
    private final HtmlRenderer mdRenderer;

    public WikiController(ProjectPaths paths, FrontmatterParser frontmatterParser, PageWriter pageWriter) {
        this.paths = paths;
        this.frontmatterParser = frontmatterParser;
        this.pageWriter = pageWriter;
        MutableDataSet options = new MutableDataSet();
        this.mdParser = Parser.builder(options).build();
        this.mdRenderer = HtmlRenderer.builder(options).build();
    }

    @GetMapping("/wiki/tree")
    public String tree(Model model) {
        model.addAttribute("nodes", buildTree(paths.wikiDir(), paths.wikiDir()));
        return "fragments/tree :: tree";
    }

    @GetMapping("/wiki/page")
    public String page(@RequestParam String path, Model model) {
        Path file = paths.wikiDir().resolve(path);
        if (!Files.exists(file)) {
            model.addAttribute("html", "<p class='muted'>Page not found.</p>");
            model.addAttribute("raw", "");
            model.addAttribute("path", path);
            return "fragments/page-view :: view";
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            FrontmatterParser.ParsedPage parsed = frontmatterParser.parse(raw);
            String html = mdRenderer.render(mdParser.parse(parsed.body()));
            model.addAttribute("html", html);
            model.addAttribute("raw", raw);
            model.addAttribute("path", path);
            return "fragments/page-view :: view";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PostMapping("/wiki/page")
    public ResponseEntity<String> savePage(@RequestParam String path,
                                           @RequestBody String content) {
        Path file = paths.wikiDir().resolve(path);
        pageWriter.write(file, content);
        return ResponseEntity.ok("saved");
    }

    // ── Tree builder ─────────────────────────────────────────────────────────

    public record TreeNode(String name, String path, boolean isDir, List<TreeNode> children) {}

    private List<TreeNode> buildTree(Path dir, Path wikiRoot) {
        List<TreeNode> nodes = new ArrayList<>();
        if (!Files.exists(dir)) return nodes;
        try (var entries = Files.list(dir).sorted()) {
            entries.forEach(entry -> {
                String name = entry.getFileName().toString();
                String relPath = wikiRoot.relativize(entry).toString();
                if (Files.isDirectory(entry)) {
                    nodes.add(new TreeNode(name, relPath, true, buildTree(entry, wikiRoot)));
                } else if (name.endsWith(".md")) {
                    nodes.add(new TreeNode(name.replace(".md", ""), relPath, false, List.of()));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return nodes;
    }
}
