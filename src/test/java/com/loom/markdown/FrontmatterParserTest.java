package com.loom.markdown;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrontmatterParserTest {

    private final FrontmatterParser parser = new FrontmatterParser();

    @Test
    void roundTrip_mutateAndReparse() {
        String original = """
                ---
                title: My Page
                type: concept
                ---

                Body content here.
                """;

        FrontmatterParser.ParsedPage parsed = parser.parse(original);
        assertThat(parsed.frontmatter()).containsEntry("title", "My Page");
        assertThat(parsed.frontmatter()).containsEntry("type", "concept");
        assertThat(parsed.body()).contains("Body content here.");

        parsed.frontmatter().put("title", "Updated Page");
        parsed.frontmatter().put("tags", java.util.List.of("a", "b"));

        String written = parser.write(parsed.frontmatter(), parsed.body());
        FrontmatterParser.ParsedPage reparsed = parser.parse(written);

        assertThat(reparsed.frontmatter()).containsEntry("title", "Updated Page");
        assertThat(reparsed.frontmatter()).containsEntry("type", "concept");
        assertThat(reparsed.frontmatter().get("tags")).isEqualTo(java.util.List.of("a", "b"));
        assertThat(reparsed.body()).contains("Body content here.");
    }

    @Test
    void noFrontmatter_returnsEmptyMapAndFullBody() {
        String raw = "Just a body.\n";
        FrontmatterParser.ParsedPage parsed = parser.parse(raw);
        assertThat(parsed.frontmatter()).isEmpty();
        assertThat(parsed.body()).isEqualTo(raw);
    }

    @Test
    void emptyFrontmatter_writesNoDelimiters() {
        String result = parser.write(Map.of(), "body text\n");
        assertThat(result).isEqualTo("body text\n");
    }
}
