# Loom

## Project Overview
Loom is a local-first, chat-first AI brainstorming tool. Users discuss a product idea in conversation; an AI partner asks Socratic questions, then extracts typed "findings." Curated findings are integrated into a local markdown wiki that accretes into a knowledge graph. The moat is the wiki — chat is ephemeral scaffolding; the synthesised wiki is the durable product.

## Tech Stack
- **Language**: Java 17
- **Framework**: Spring Boot 3.x
- **Build tool**: Maven
- **AI**: Anthropic Java SDK — model `claude-sonnet-4-6`
- **Database**: SQLite via `org.xerial:sqlite-jdbc` + Spring JDBC
- **Markdown/YAML**: `com.vladsch.flexmark` (wikilink parsing), `org.yaml:snakeyaml` (frontmatter)
- **Frontend**: Thymeleaf + HTMX (in-process, no separate build step)
- **Testing**: JUnit 5, AssertJ

## Critical Invariant — State This Before Every Index Change
**The markdown wiki is the single source of truth. The SQLite index is a rebuildable derivative.**
If you cannot delete `loom.db` and fully regenerate it from the markdown files, the design is wrong.
Never let the index become load-bearing.

## Project Structure

```
loom/
├── pom.xml
├── .env.example
├── src/main/java/com/loom/
│   ├── LoomApplication.java
│   ├── config/LoomConfig.java          # loom.project-root, API key binding
│   ├── markdown/
│   │   ├── FrontmatterParser.java      # YAML frontmatter round-trip
│   │   ├── WikilinkParser.java         # [[wikilink]] extraction
│   │   └── PageWriter.java             # atomic markdown file writes
│   ├── storage/
│   │   ├── ProjectPaths.java           # project-root-aware path resolution
│   │   ├── RawSessionStore.java        # raw/ append-only per-session files
│   │   └── WikiPageStore.java          # wiki/ read/write/create
│   ├── index/
│   │   ├── SqliteIndexStore.java       # CRUD for findings + links tables
│   │   └── IndexRebuildService.java    # wipe → walk markdown → repopulate
│   ├── ai/
│   │   ├── AnthropicClient.java        # thin SDK wrapper
│   │   ├── QuestioningService.java     # Socratic chat loop
│   │   └── FindingExtractor.java       # conversation → List<FindingProposal>
│   ├── session/
│   │   ├── SessionService.java
│   │   └── ChatMessage.java
│   ├── savegate/
│   │   ├── SaveGateService.java        # curate → raw + wiki, atomic
│   │   └── IntegrationService.java     # weave finding into wiki page
│   ├── linking/
│   │   └── CrossSessionLinker.java     # session-end background pass
│   └── web/
│       ├── ChatController.java
│       ├── WikiController.java
│       └── GraphController.java
└── src/main/resources/
    ├── application.yml
    ├── schema.sql                      # SQLite DDL
    └── templates/                      # Thymeleaf
```

## Wiki Storage Layout (on disk)

```
projects/<project-name>/
  raw/                        ← capture layer: one file per session (append-only)
    2026-06-14-pricing.md
  wiki/                       ← synthesised layer: living pages by page type
    index.md                  ← project spine / synthesised overview
    concepts/
    decisions/
    flows/
    log.md                    ← ## [YYYY-MM-DD] <op> | <title>
```

Finding type → page mapping:
- `insight` / `pattern` → `concepts/`
- `constraint` → `decisions/` or `concepts/`
- `tension` → `## Open tensions` section of affected page (never overwrite prose)
- `question` → open item on the relevant page
- `artifact` → own section or sub-note

## Key Conventions
- Constructor injection throughout — no `@Autowired` field injection
- All path resolution goes through `ProjectPaths`; never construct wiki paths ad-hoc
- Raw session files are append-only — never rewrite them
- Contradictions are flagged in `## Open tensions`, never silently resolved
- `log.md` entries use greppable prefix: `## [YYYY-MM-DD] <op> | <title>`
- `[[wikilinks]]` are Obsidian-compatible throughout
- Multi-project is additive: `ProjectPaths` takes a project root; v1 passes one root from config

## Environment Setup

1. Copy `.env.example` to `.env` and set `ANTHROPIC_API_KEY`
2. Set `loom.project-root` in `application.yml` (or override via env var `LOOM_PROJECT_ROOT`)
3. `mvn spring-boot:run`
4. To wipe and rebuild the index: `POST /api/index/rebuild`

## API Key
`ANTHROPIC_API_KEY` — must be set as an environment variable. Never commit to source.

## Tests That Must Always Stay Green
- `FrontmatterParserTest` — frontmatter round-trip
- `WikilinkParserTest` — `[[link]]` and `[[link|alias]]` extraction
- `IndexRebuildTest` — wipe → rebuild → identical rows (guards the core invariant)
- `ContradictionFlagTest` — contradicting finding writes `## Open tensions`, not overwrite

## Notes for Claude
- Always check `TASK.md` before starting work
- Phase verification checkpoints: finish and test the whole phase before moving to the next
- Never let the SQLite index become load-bearing — if a feature requires the index to exist to function correctly, the design is wrong
- Do not build the frontend (Phase 6) until Phases 1–5 are complete and tested
- Keep `ANTHROPIC_API_KEY` out of source — env var only
