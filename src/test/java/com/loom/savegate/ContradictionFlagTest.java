package com.loom.savegate;

import com.loom.ai.FindingProposal;
import com.loom.markdown.FrontmatterParser;
import com.loom.markdown.PageWriter;
import com.loom.storage.ProjectPaths;
import com.loom.config.LoomConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContradictionFlagTest {

    @TempDir
    Path tempDir;

    IntegrationService integrationService;

    @BeforeEach
    void setUp() {
        LoomConfig config = new LoomConfig();
        config.setProjectRoot(tempDir.toString());
        ProjectPaths paths = new ProjectPaths(config);
        FrontmatterParser parser = new FrontmatterParser();
        PageWriter writer = new PageWriter();
        integrationService = new IntegrationService(paths, parser, writer);
    }

    @Test
    void tension_writesToOpenTensionsSection_notOverwritingProse() throws IOException {
        // Seed index.md with existing prose
        Path wikiDir = tempDir.resolve("wiki");
        Files.createDirectories(wikiDir);
        String existingContent = """
                # Index

                This is the project overview. It has important prose that must not be overwritten.

                ## Goals

                - Ship fast
                - Stay simple
                """;
        Files.writeString(wikiDir.resolve("index.md"), existingContent, StandardCharsets.UTF_8);

        FindingProposal tension = new FindingProposal(
                "tension",
                "Simplicity vs enterprise features",
                "The user wants a simple product but enterprise clients demand configurability."
        );

        integrationService.integrate(tension, "session-test-001");

        String result = Files.readString(wikiDir.resolve("index.md"), StandardCharsets.UTF_8);

        // Original prose must be intact
        assertThat(result).contains("This is the project overview. It has important prose that must not be overwritten.");
        assertThat(result).contains("## Goals");
        assertThat(result).contains("- Ship fast");

        // Tension must appear in Open tensions section
        assertThat(result).contains("## Open tensions");
        assertThat(result).contains("Simplicity vs enterprise features");
        assertThat(result).contains("session-test-001");
    }

    @Test
    void tension_addsOpenTensionsSection_whenAbsent() throws IOException {
        Path wikiDir = tempDir.resolve("wiki");
        Files.createDirectories(wikiDir);
        Files.writeString(wikiDir.resolve("index.md"), "# Index\n\nSome prose.\n", StandardCharsets.UTF_8);

        FindingProposal tension = new FindingProposal("tension", "Speed vs quality", "Fast shipping risks quality.");
        integrationService.integrate(tension, "session-abc");

        String result = Files.readString(wikiDir.resolve("index.md"), StandardCharsets.UTF_8);
        assertThat(result).contains("## Open tensions");
        assertThat(result).contains("Speed vs quality");
        // Original prose must still be present
        assertThat(result).contains("Some prose.");
    }

    @Test
    void multipleTensions_allAppendedNotOverwritten() throws IOException {
        Path wikiDir = tempDir.resolve("wiki");
        Files.createDirectories(wikiDir);
        Files.writeString(wikiDir.resolve("index.md"), "# Index\n\n## Open tensions\n\n- existing tension\n",
                StandardCharsets.UTF_8);

        integrationService.integrate(new FindingProposal("tension", "First new tension", "Body one."), "s1");
        integrationService.integrate(new FindingProposal("tension", "Second new tension", "Body two."), "s2");

        String result = Files.readString(wikiDir.resolve("index.md"), StandardCharsets.UTF_8);
        assertThat(result).contains("existing tension");
        assertThat(result).contains("First new tension");
        assertThat(result).contains("Second new tension");
        // Only one ## Open tensions heading
        assertThat(result.split("## Open tensions", -1).length - 1).isEqualTo(1);
    }

    @Test
    void insightFinding_doesNotTouchOpenTensions() throws IOException {
        Path wikiDir = tempDir.resolve("wiki");
        Files.createDirectories(wikiDir);
        Files.writeString(wikiDir.resolve("index.md"),
                "# Index\n\n## Open tensions\n\n- pre-existing tension\n", StandardCharsets.UTF_8);

        FindingProposal insight = new FindingProposal("insight", "Users prefer flat pricing", "Flat pricing reduces friction.");
        integrationService.integrate(insight, "session-xyz");

        String indexContent = Files.readString(wikiDir.resolve("index.md"), StandardCharsets.UTF_8);
        // Open tensions must be untouched
        assertThat(indexContent).contains("pre-existing tension");
        assertThat(indexContent).doesNotContain("flat pricing");

        // Insight goes to concepts/
        Path conceptFile = tempDir.resolve("wiki/concepts/users-prefer-flat-pricing.md");
        assertThat(conceptFile).exists();
        assertThat(Files.readString(conceptFile)).contains("Flat pricing reduces friction.");
    }
}
