package com.loom.savegate;

import com.loom.ai.FindingProposal;
import com.loom.index.SqliteIndexStore;
import com.loom.storage.ProjectPaths;
import com.loom.storage.RawSessionStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Accepts curated findings and atomically:
 * 1. Appends typed bullets to the raw session file
 * 2. Integrates each finding into the wiki
 * 3. Records each finding in the SQLite index
 * 4. Appends a log.md entry
 */
@Service
public class SaveGateService {

    private final RawSessionStore rawSessionStore;
    private final IntegrationService integrationService;
    private final SqliteIndexStore indexStore;
    private final ProjectPaths paths;

    public SaveGateService(RawSessionStore rawSessionStore,
                           IntegrationService integrationService,
                           SqliteIndexStore indexStore,
                           ProjectPaths paths) {
        this.rawSessionStore = rawSessionStore;
        this.integrationService = integrationService;
        this.indexStore = indexStore;
        this.paths = paths;
    }

    public void save(String sessionId, String topic, List<FindingProposal> curated) {
        Path sessionFile = rawSessionStore.create(topic);

        for (FindingProposal finding : curated) {
            // 1. Append to raw session file
            rawSessionStore.append(sessionFile,
                    "- [" + finding.type().toUpperCase() + "] **" + finding.title() + "**: " + finding.body());

            // 2. Integrate into wiki
            String pagePath = integrationService.integrate(finding, sessionId);

            // 3. Persist to index
            indexStore.insertFinding(sessionId, finding.type(), finding.title(), finding.body(), pagePath);
        }

        // 4. Append to log.md
        appendLog(sessionId, topic, curated.size());
    }

    private void appendLog(String sessionId, String topic, int count) {
        Path logFile = paths.wikiLog();
        try {
            Files.createDirectories(logFile.getParent());
            String entry = "## [" + LocalDate.now() + "] save | " + topic
                    + " (" + count + " finding" + (count == 1 ? "" : "s") + ", session " + sessionId + ")\n\n";
            Files.writeString(logFile, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
