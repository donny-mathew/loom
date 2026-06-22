# Loom

A local-first, chat-first AI brainstorming tool. You discuss a product idea in conversation; an AI partner asks Socratic questions, extracts typed findings, and weaves them into a local markdown wiki that accretes into a knowledge graph.

**The wiki is the product.** Chat is ephemeral scaffolding. The synthesised markdown is what compounds over time.

---

## How it works

1. **Start a session** — give it a topic (e.g. "pricing model for small teams")
2. **Chat** — the AI asks one focused Socratic question at a time, pushing you to examine assumptions and surface constraints
3. **Extract findings** — a second AI pass distills the conversation into typed findings: `insight`, `pattern`, `constraint`, `tension`, `question`, `artifact`
4. **Curate** — keep, reject, or merge each proposal
5. **Save to wiki** — accepted findings are routed to the right markdown page, woven into prose, and indexed
6. **Cross-session linking** — when you close a session, a background pass classifies relationships between wiki pages (`supports`, `contradicts`, `generalises`, `relates-to`) and flags concepts that keep emerging across sessions

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Build | Maven |
| AI | Anthropic Java SDK — `claude-sonnet-4-6` |
| Database | SQLite (rebuildable index — never load-bearing) |
| Markdown / YAML | flexmark + snakeyaml |
| Frontend | Thymeleaf + HTMX (no build step) |
| Testing | JUnit 5 + AssertJ |

---

## Architecture

```
projects/<project-name>/
  raw/                  ← append-only capture layer (one file per session)
  wiki/
    index.md            ← project spine, synthesised overview
    log.md              ← greppable history of every save operation
    concepts/           ← insights, patterns, artifacts
    decisions/          ← constraints, trade-offs
    flows/              ← process / sequence pages
```

The SQLite index (`loom.db`) mirrors the wiki for fast graph queries. It can always be fully rebuilt from the markdown files:

```
POST /api/index/rebuild
```

---

## Getting started

### Prerequisites

- Java 17+
- Maven 3.9+
- An Anthropic API key

### Setup

```bash
git clone https://github.com/donny-mathew/loom.git
cd loom
cp .env.example .env
# Edit .env — set ANTHROPIC_API_KEY and LOOM_PROJECT_ROOT
```

### Run

```bash
export $(cat .env | xargs)
mvn spring-boot:run
```

App starts on `http://localhost:8080`.

---

## API overview

| Endpoint | Description |
|---|---|
| `POST /api/chat/session` | Create a new brainstorming session |
| `POST /api/chat/message?sessionId=` | Send a message (streams AI reply) |
| `POST /api/chat/extract-findings?sessionId=` | Distill conversation into finding proposals |
| `POST /api/chat/save?sessionId=&topic=` | Save curated findings to wiki + index |
| `DELETE /api/chat/session?sessionId=` | Close session, trigger cross-session linker |
| `POST /api/index/rebuild` | Wipe and rebuild the SQLite index from markdown |

---

## Core invariant

> The markdown wiki is the single source of truth. The SQLite index is a rebuildable derivative.

If you cannot delete `loom.db` and fully regenerate it from the markdown files, the design is wrong.

---

## Running tests

```bash
mvn test
```

Key tests that must always stay green:

| Test | What it guards |
|---|---|
| `FrontmatterParserTest` | YAML frontmatter round-trip |
| `WikilinkParserTest` | `[[link]]` and `[[link\|alias]]` extraction |
| `IndexRebuildTest` | Wipe → rebuild → identical rows (core invariant) |
| `ContradictionFlagTest` | Tensions go to `## Open tensions`, never overwrite prose |
