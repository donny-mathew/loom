package com.loom.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikilinkParserTest {

    private final WikilinkParser parser = new WikilinkParser();

    @Test
    void plainLink() {
        List<String> links = parser.extract("See [[pricing model]] for details.");
        assertThat(links).containsExactly("pricing model");
    }

    @Test
    void aliasedLink() {
        List<String> links = parser.extract("See [[pricing model|this page]] for details.");
        assertThat(links).containsExactly("pricing model");
    }

    @Test
    void multipleLinks() {
        List<String> links = parser.extract("[[ConceptA]] relates to [[ConceptB|B alias]] and [[ConceptC]].");
        assertThat(links).containsExactly("ConceptA", "ConceptB", "ConceptC");
    }

    @Test
    void noLinks_returnsEmpty() {
        List<String> links = parser.extract("No wikilinks here.");
        assertThat(links).isEmpty();
    }
}
