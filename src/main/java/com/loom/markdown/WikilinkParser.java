package com.loom.markdown;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts page names from Obsidian-compatible [[wikilinks]] and [[link|alias]] syntax.
 */
@Component
public class WikilinkParser {

    // Matches [[target]] and [[target|alias]]
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?]]");

    public List<String> extract(String markdown) {
        List<String> links = new ArrayList<>();
        Matcher m = WIKILINK.matcher(markdown);
        while (m.find()) {
            links.add(m.group(1).strip());
        }
        return links;
    }
}
