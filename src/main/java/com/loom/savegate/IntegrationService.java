package com.loom.savegate;

import com.loom.ai.FindingProposal;
import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.PageWriter;
import com.loom.storage.ProjectPaths;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes a curated finding to the correct wiki page and weaves it into prose.
 * Contradictions and tensions always go to ## Open tensions — never silent overwrites.
 */
@Service
public class IntegrationService {

    private final ProjectPaths paths;
    private final FrontmatterParser frontmatterParser;
    private final PageWriter pageWriter;

    public IntegrationService(ProjectPaths paths, FrontmatterParser frontmatterParser, PageWriter pageWriter) {
        this.paths = paths;
        this.frontmatterParser = frontmatterParser;
        this.pageWriter = pageWriter;
    }

    /**
     * Integrates a finding into the wiki. Returns the relative page path it was written to.
     */
    public String integrate(FindingProposal finding, String sessionId) {
        return switch (finding.type()) {
            case "tension" -> integrateTension(finding, sessionId);
            case "question" -> integrateQuestion(finding, sessionId);
            case "constraint" -> integrateTyped(finding, sessionId, "decisions");
            default -> integrateTyped(finding, sessionId, "concepts"); // insight, pattern, artifact
        };
    }

    // ── Typed page (concepts / decisions) ───────────────────────────────────

    private String integrateTyped(FindingProposal finding, String sessionId, String pageType) {
        String filename = slugify(finding.title()) + ".md";
        Path file = paths.wikiPage(pageType, filename);
        ensureDir(file.getParent());

        String updatedBody;
        Map<String, Object> fm;

        if (Files.exists(file)) {
            FrontmatterParser.ParsedPage existing = parseFile(file);
            fm = existing.frontmatter();
            updatedBody = existing.body() + "\n" + bodyEntry(finding, sessionId);
        } else {
            fm = newFrontmatter(finding, sessionId);
            updatedBody = "## " + finding.title() + "\n\n" + finding.body() + "\n";
        }

        pageWriter.write(file, frontmatterParser.write(fm, updatedBody));
        return pageType + "/" + filename;
    }

    // ── Tension → ## Open tensions (never overwrites prose) ────────────────

    private String integrateTension(FindingProposal finding, String sessionId) {
        // Tensions are appended to the index.md open tensions section
        Path file = paths.wikiIndex();
        ensureDir(file.getParent());

        String tensionEntry = "- **" + finding.title() + "** (" + sessionId + "): " + finding.body();

        if (!Files.exists(file)) {
            String content = "# Index\n\n## Open tensions\n\n" + tensionEntry + "\n";
            pageWriter.write(file, content);
        } else {
            String raw = readFile(file);
            if (raw.contains("## Open tensions")) {
                // Append after the section header, before the next ## or EOF
                String updated = insertAfterSection(raw, "## Open tensions", tensionEntry);
                pageWriter.write(file, updated);
            } else {
                // Add section at end
                pageWriter.write(file, raw.stripTrailing() + "\n\n## Open tensions\n\n" + tensionEntry + "\n");
            }
        }
        return "index.md";
    }

    // ── Question → open item on relevant page ───────────────────────────────

    private String integrateQuestion(FindingProposal finding, String sessionId) {
        String filename = "open-questions.md";
        Path file = paths.wikiPage("concepts", filename);
        ensureDir(file.getParent());

        String entry = "- [ ] **" + finding.title() + "** (" + sessionId + "): " + finding.body();

        if (!Files.exists(file)) {
            pageWriter.write(file, "# Open Questions\n\n" + entry + "\n");
        } else {
            appendToFile(file, entry);
        }
        return "concepts/" + filename;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String bodyEntry(FindingProposal finding, String sessionId) {
        return "### " + finding.title() + " (" + sessionId + ")\n\n" + finding.body() + "\n";
    }

    private Map<String, Object> newFrontmatter(FindingProposal finding, String sessionId) {
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("title", finding.title());
        fm.put("type", finding.type());
        fm.put("session-id", sessionId);
        fm.put("created", LocalDate.now().toString());
        return fm;
    }

    /**
     * Inserts a new line after a section header, before the next ## heading or EOF.
     */
    String insertAfterSection(String raw, String sectionHeader, String newLine) {
        int headerPos = raw.indexOf(sectionHeader);
        if (headerPos == -1) return raw;

        int afterHeader = raw.indexOf('\n', headerPos);
        if (afterHeader == -1) return raw + "\n" + newLine;

        // Find where the next ## heading starts (or EOF)
        int nextSection = raw.indexOf("\n##", afterHeader + 1);
        if (nextSection == -1) {
            return raw.stripTrailing() + "\n" + newLine + "\n";
        }
        return raw.substring(0, nextSection) + "\n" + newLine + raw.substring(nextSection);
    }

    private FrontmatterParser.ParsedPage parseFile(Path file) {
        return frontmatterParser.parse(readFile(file));
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void appendToFile(Path file, String line) {
        try {
            Files.writeString(file, "\n" + line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String slugify(String title) {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
