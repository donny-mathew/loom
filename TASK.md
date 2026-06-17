# TASK.md — Loom

## 🎯 Goal
Build a local-first, chat-first AI brainstorming tool backed by an accreting LLM-wiki: chat is ephemeral scaffolding; the synthesised markdown wiki is the durable product.

---

## 📋 Active Tasks

### Phase 1 — Project Scaffold + Markdown Layer (NO AI)

| Status | Task | Notes |
|--------|------|-------|
| ✅ | Initialise Maven project with Spring Boot 3.x, Java 17 | `pom.xml` with spring-boot-starter-parent |
| ✅ | Declare all dependencies: flexmark, snakeyaml, sqlite-jdbc, Anthropic SDK, Thymeleaf, HTMX webjars | Pin versions in `pom.xml` |
| ✅ | Create `LoomApplication.java` and `application.yml` with `loom.project-root` config property | |
| ✅ | Implement `ProjectPaths` — project-root-aware path resolution (no hardcoded single-project assumption) | |
| ✅ | Implement `FrontmatterParser` — YAML frontmatter round-trip (parse / write) | |
| ✅ | Implement `WikilinkParser` — extract page names from `[[link]]` and `[[link\|alias]]` | |
| ✅ | Implement `PageWriter` — write or overwrite a markdown file atomically | |
| ✅ | Implement `RawSessionStore` — create and append-only write to `raw/<date>-<topic>.md` | Frontmatter + typed bullets |
| ✅ | Implement `WikiPageStore` — read / create / update wiki pages under `wiki/` | Creates page-type dirs if absent |
| ✅ | Scaffold wiki template files: `index.md`, `log.md`, page-type dirs (`concepts/`, `decisions/`, `flows/`) | Created at project init via `WikiInitializer` |
| ✅ | Write `FrontmatterParserTest` — round-trip: parse → mutate → write → re-parse | 3/3 passing |
| ✅ | Write `WikilinkParserTest` — plain links and aliased links | 4/4 passing |

### Phase 2 — SQLite Index (Rebuildable Mirror)

| Status | Task | Notes |
|--------|------|-------|
| ⚪ | Write `schema.sql` — `findings` and `links` tables | |
| ⚪ | Implement `SqliteIndexStore` — CRUD for findings + links | Spring JDBC |
| ⚪ | Implement `IndexRebuildService` — wipe tables, walk markdown tree, repopulate | Must produce identical data to pre-wipe state |
| ⚪ | Write `IndexRebuildTest` — wipe → rebuild → assert row-for-row equivalence | Key invariant test |

### Phase 3 — Chat Endpoint + Anthropic API

| Status | Task | Notes |
|--------|------|-------|
| ⚪ | Implement `AnthropicClient` — thin SDK wrapper reading `ANTHROPIC_API_KEY` from env | |
| ⚪ | Implement `QuestioningService` — Socratic system prompt, streaming chat | Returns `List<ChatMessage>` |
| ⚪ | Implement `FindingExtractor` — second AI call on conversation; returns `List<FindingProposal>` (typed JSON) | |
| ⚪ | Implement `SessionService` — create / close sessions, manage in-memory conversation state | |
| ⚪ | Implement `ChatController` — `POST /api/chat/message`, `POST /api/chat/extract-findings` | |

### Phase 4 — Save Gate + Integration-on-Save

| Status | Task | Notes |
|--------|------|-------|
| ⚪ | Implement `SaveGateService` — accept curated findings, atomically append raw + call integration | |
| ⚪ | Implement `IntegrationService` — route finding to page, weave into prose, add wikilinks, update index.md | |
| ⚪ | Contradiction detection — write to `## Open tensions` section, never silently overwrite | |
| ⚪ | Append entry to `log.md` for every save op | Greppable `## [YYYY-MM-DD] <op> \| <title>` format |
| ⚪ | Write `ContradictionFlagTest` — contradicting finding produces `## Open tensions` entry, not overwrite | |

### Phase 5 — Cross-Session Linking

| Status | Task | Notes |
|--------|------|-------|
| ⚪ | Implement `CrossSessionLinker` — session-end background pass: typed wikilinks + edges + convergence detection | Async, not per-save |
| ⚪ | Persist typed relationship edges (`supports`, `contradicts`, `generalises`, `relates-to`) to index | |

### Phase 6 — Frontend

| Status | Task | Notes |
|--------|------|-------|
| ⚪ | Thymeleaf base layout with Chat / Graph / Notes tabs | |
| ⚪ | Chat tab — conversation stream + finding proposal panel (keep / reject / merge per finding) | |
| ⚪ | Graph tab — force-directed wikilink graph from index edges | D3 or vanilla JS |
| ⚪ | Notes tab — browse and edit wiki pages | |
| ⚪ | Wire up `WikiController` and `GraphController` for Notes and Graph data endpoints | |

---

## ✅ Completed

- ✅ Phase 1 — Project Scaffold + Markdown Layer (all 12 tasks, 7/7 tests green, 2026-06-16)
