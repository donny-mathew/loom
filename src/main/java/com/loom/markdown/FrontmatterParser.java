package com.loom.markdown;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses and writes YAML frontmatter delimited by --- lines.
 */
@Component
public class FrontmatterParser {

    private static final String DELIMITER = "---";

    private final Yaml yaml;

    public FrontmatterParser() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }

    public record ParsedPage(Map<String, Object> frontmatter, String body) {}

    public ParsedPage parse(String raw) {
        if (!raw.startsWith(DELIMITER)) {
            return new ParsedPage(new LinkedHashMap<>(), raw);
        }
        int end = raw.indexOf("\n" + DELIMITER, DELIMITER.length());
        if (end == -1) {
            return new ParsedPage(new LinkedHashMap<>(), raw);
        }
        String yamlBlock = raw.substring(DELIMITER.length(), end).strip();
        String body = raw.substring(end + ("\n" + DELIMITER).length());
        if (body.startsWith("\n")) {
            body = body.substring(1);
        }
        Map<String, Object> fm = yaml.load(yamlBlock);
        return new ParsedPage(fm != null ? fm : new LinkedHashMap<>(), body);
    }

    public String write(Map<String, Object> frontmatter, String body) {
        if (frontmatter == null || frontmatter.isEmpty()) {
            return body;
        }
        String yamlBlock = yaml.dump(frontmatter);
        return DELIMITER + "\n" + yamlBlock + DELIMITER + "\n" + body;
    }
}
