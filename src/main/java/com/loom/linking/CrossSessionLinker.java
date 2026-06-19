package com.loom.linking;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.ai.LoomAnthropicClient;
import com.loom.index.SqliteIndexStore;
import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.PageWriter;
import com.loom.markdown.WikilinkParser;
import com.loom.storage.ProjectPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Async session-end pass that:
 * 1. Walks wiki pages and classifies wikilink relationships via AI
 *    (supports | contradicts | generalises | relates-to)
 * 2. Persists typed edges to the links index
 * 3. Detects convergence (pages referenced by 3+ distinct sessions)
 *    and annotates their frontmatter with converging: true
 */
@Service
public class CrossSessionLinker {

    private static final Logger log = LoggerFactory.getLogger(CrossSessionLinker.class);
    private static final int CONVERGENCE_THRESHOLD = 3;

    private static final String CLASSIFY_PROMPT = """
            You are a knowledge graph assistant. Given pairs of wiki page excerpts, classify the
            relationship of the SOURCE page toward the TARGET page.

            Relationship types:
            - supports: source provides evidence or reasoning that strengthens the target
            - contradicts: source presents a tension or counter-evidence to the target
            - generalises: source is a broader concept that subsumes the target
            - relates-to: source references or is thematically connected to the target (default)

            Input: a JSON array of objects with "source", "sourceBody", "target", "targetBody".
            Output: a JSON array of objects with "source", "target", "relationship" — same order as input.
            Output ONLY valid JSON, no markdown fences.
            """;

    private final ProjectPaths paths;
    private final WikilinkParser wikilinkParser;
    private final FrontmatterParser frontmatterParser;
    private final PageWriter pageWriter;
    private final SqliteIndexStore indexStore;
    private final LoomAnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public CrossSessionLinker(ProjectPaths paths,
                               WikilinkParser wikilinkParser,
                               FrontmatterParser frontmatterParser,
                               PageWriter pageWriter,
                               SqliteIndexStore indexStore,
                               LoomAnthropicClient anthropicClient,
                               ObjectMapper objectMapper) {
        this.paths = paths;
        this.wikilinkParser = wikilinkParser;
        this.frontmatterParser = frontmatterParser;
        this.pageWriter = pageWriter;
        this.indexStore = indexStore;
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    @Async
    public void runForSession(String sessionId) {
        log.info("CrossSessionLinker: starting pass for session {}", sessionId);
        try {
            Path wikiDir = paths.wikiDir();
            if (!Files.exists(wikiDir)) return;

            Map<String, String> pageIndex = buildPageIndex(wikiDir);
            if (pageIndex.isEmpty()) return;

            List<LinkCandidate> candidates = collectCandidates(pageIndex, wikiDir);
            if (!candidates.isEmpty()) {
                List<TypedEdge> edges = classifyEdges(candidates, pageIndex);
                persistEdges(edges);
                detectAndMarkConvergence(edges);
            }

            log.info("CrossSessionLinker: pass complete for session {} — {} candidates", sessionId, candidates.size());
        } catch (Exception e) {
            log.error("CrossSessionLinker: failed for session {}", sessionId, e);
        }
    }

    // ── Page index: title/slug → body snippet ───────────────────────────────

    private Map<String, String> buildPageIndex(Path wikiDir) throws IOException {
        Map<String, String> index = new LinkedHashMap<>();
        try (var walk = Files.walk(wikiDir)) {
            walk.filter(p -> p.toString().endsWith(".md"))
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String raw = Files.readString(file, StandardCharsets.UTF_8);
                        FrontmatterParser.ParsedPage page = frontmatterParser.parse(raw);
                        String title = getTitle(page, file);
                        // Store up to 400 chars of body as the snippet for AI comparison
                        String snippet = page.body().strip();
                        if (snippet.length() > 400) snippet = snippet.substring(0, 400);
                        index.put(title, snippet);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        return index;
    }

    // ── Candidate collection: (sourcePage, targetTitle) pairs ───────────────

    private List<LinkCandidate> collectCandidates(Map<String, String> pageIndex, Path wikiDir) throws IOException {
        List<LinkCandidate> candidates = new ArrayList<>();
        try (var walk = Files.walk(wikiDir)) {
            walk.filter(p -> p.toString().endsWith(".md"))
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String raw = Files.readString(file, StandardCharsets.UTF_8);
                        FrontmatterParser.ParsedPage page = frontmatterParser.parse(raw);
                        String sourceTitle = getTitle(page, file);
                        String relPath = paths.wikiDir().relativize(file).toString();

                        for (String target : wikilinkParser.extract(raw)) {
                            if (!target.equals(sourceTitle) && pageIndex.containsKey(target)) {
                                candidates.add(new LinkCandidate(sourceTitle, relPath, target));
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        return candidates;
    }

    // ── AI classification ────────────────────────────────────────────────────

    private List<TypedEdge> classifyEdges(List<LinkCandidate> candidates, Map<String, String> pageIndex) {
        List<Map<String, String>> input = candidates.stream().map(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("source", c.sourceTitle());
            m.put("sourceBody", pageIndex.getOrDefault(c.sourceTitle(), ""));
            m.put("target", c.targetTitle());
            m.put("targetBody", pageIndex.getOrDefault(c.targetTitle(), ""));
            return m;
        }).toList();

        try {
            String inputJson = objectMapper.writeValueAsString(input);
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_6)
                    .maxTokens(2048L)
                    .system(CLASSIFY_PROMPT)
                    .addUserMessage(inputJson)
                    .build();

            String responseJson = anthropicClient.get().messages().create(params)
                    .content().stream()
                    .flatMap(b -> b.text().stream())
                    .map(t -> t.text())
                    .findFirst()
                    .orElse("[]");

            List<Map<String, String>> results = objectMapper.readValue(
                    responseJson.strip(), new TypeReference<>() {});

            List<TypedEdge> edges = new ArrayList<>();
            for (int i = 0; i < Math.min(candidates.size(), results.size()); i++) {
                LinkCandidate c = candidates.get(i);
                String rel = results.get(i).getOrDefault("relationship", "relates-to");
                edges.add(new TypedEdge(c.sourcePath(), c.targetTitle(), rel));
            }
            return edges;
        } catch (Exception e) {
            log.warn("CrossSessionLinker: classification failed, defaulting to relates-to", e);
            return candidates.stream()
                    .map(c -> new TypedEdge(c.sourcePath(), c.targetTitle(), "relates-to"))
                    .toList();
        }
    }

    // ── Persist edges ────────────────────────────────────────────────────────

    private void persistEdges(List<TypedEdge> edges) {
        // Replace existing links for these source paths with typed versions
        Set<String> sources = edges.stream().map(TypedEdge::sourcePath).collect(Collectors.toSet());
        indexStore.deleteAllLinks();  // full refresh — links are always rebuildable
        for (TypedEdge edge : edges) {
            indexStore.insertLink(edge.sourcePath(), edge.targetPath(), edge.relationship());
        }
    }

    // ── Convergence detection ────────────────────────────────────────────────

    private void detectAndMarkConvergence(List<TypedEdge> edges) {
        // Count distinct source pages per target (proxy for session convergence)
        Map<String, Long> targetRefCount = edges.stream()
                .collect(Collectors.groupingBy(TypedEdge::targetPath, Collectors.counting()));

        targetRefCount.forEach((target, count) -> {
            if (count >= CONVERGENCE_THRESHOLD) {
                markConverging(target);
            }
        });
    }

    private void markConverging(String targetTitle) {
        // Find the page file by walking wiki and matching title
        try {
            Path wikiDir = paths.wikiDir();
            if (!Files.exists(wikiDir)) return;
            try (var walk = Files.walk(wikiDir)) {
                walk.filter(p -> p.toString().endsWith(".md"))
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String raw = Files.readString(file, StandardCharsets.UTF_8);
                            FrontmatterParser.ParsedPage page = frontmatterParser.parse(raw);
                            if (targetTitle.equals(getTitle(page, file))) {
                                Map<String, Object> fm = new LinkedHashMap<>(page.frontmatter());
                                fm.put("converging", true);
                                pageWriter.write(file, frontmatterParser.write(fm, page.body()));
                                log.info("CrossSessionLinker: marked {} as converging", targetTitle);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getTitle(FrontmatterParser.ParsedPage page, Path file) {
        Object t = page.frontmatter().get("title");
        return t != null ? t.toString() : file.getFileName().toString().replace(".md", "");
    }

    private record LinkCandidate(String sourceTitle, String sourcePath, String targetTitle) {}
    private record TypedEdge(String sourcePath, String targetPath, String relationship) {}
}
