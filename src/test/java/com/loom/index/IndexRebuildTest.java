package com.loom.index;

import com.loom.config.LoomConfig;
import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.WikilinkParser;
import com.loom.storage.ProjectPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IndexRebuildTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProjectRoot(DynamicPropertyRegistry registry) {
        registry.add("loom.project-root", () -> tempDir.toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("loom-test.db"));
    }

    @Autowired
    SqliteIndexStore store;

    @Autowired
    IndexRebuildService rebuildService;

    @Autowired
    FrontmatterParser frontmatterParser;

    @Autowired
    WikilinkParser wikilinkParser;

    @BeforeEach
    void seedWiki() throws IOException {
        Path wikiDir = tempDir.resolve("wiki/concepts");
        Files.createDirectories(wikiDir);

        String pageA = """
                ---
                title: Concept Alpha
                type: insight
                session-id: session-001
                ---

                Alpha body. See [[Concept Beta]].
                """;

        String pageB = """
                ---
                title: Concept Beta
                type: pattern
                session-id: session-001
                ---

                Beta body. References [[Concept Alpha|Alpha]].
                """;

        Files.writeString(wikiDir.resolve("alpha.md"), pageA, StandardCharsets.UTF_8);
        Files.writeString(wikiDir.resolve("beta.md"), pageB, StandardCharsets.UTF_8);
    }

    @Test
    void wipeAndRebuild_producesIdenticalRows() {
        // First rebuild — baseline
        rebuildService.rebuild();
        List<Finding> firstFindings = store.allFindings();
        List<Link> firstLinks = store.allLinks();

        assertThat(firstFindings).hasSize(2);
        assertThat(firstLinks).hasSize(2);

        // Wipe + second rebuild — must match
        rebuildService.rebuild();
        List<Finding> secondFindings = store.allFindings();
        List<Link> secondLinks = store.allLinks();

        assertThat(secondFindings).hasSize(firstFindings.size());
        assertThat(secondLinks).hasSize(firstLinks.size());

        // Row-for-row equivalence on business fields (ids differ after wipe)
        for (int i = 0; i < firstFindings.size(); i++) {
            Finding a = firstFindings.get(i);
            Finding b = secondFindings.get(i);
            assertThat(b.sessionId()).isEqualTo(a.sessionId());
            assertThat(b.type()).isEqualTo(a.type());
            assertThat(b.title()).isEqualTo(a.title());
            assertThat(b.body()).isEqualTo(a.body());
            assertThat(b.pagePath()).isEqualTo(a.pagePath());
        }

        for (int i = 0; i < firstLinks.size(); i++) {
            Link a = firstLinks.get(i);
            Link b = secondLinks.get(i);
            assertThat(b.sourcePath()).isEqualTo(a.sourcePath());
            assertThat(b.targetPath()).isEqualTo(a.targetPath());
            assertThat(b.relationship()).isEqualTo(a.relationship());
        }
    }

    @Test
    void findings_haveCorrectTypes() {
        rebuildService.rebuild();
        List<Finding> findings = store.allFindings();
        assertThat(findings).extracting(Finding::type).containsExactlyInAnyOrder("insight", "pattern");
    }

    @Test
    void links_captureWikilinks() {
        rebuildService.rebuild();
        List<Link> links = store.allLinks();
        assertThat(links).extracting(Link::targetPath)
                .containsExactlyInAnyOrder("Concept Beta", "Concept Alpha");
    }
}
