CREATE TABLE IF NOT EXISTS findings (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT    NOT NULL,
    type        TEXT    NOT NULL,  -- insight | pattern | constraint | tension | question | artifact
    title       TEXT    NOT NULL,
    body        TEXT    NOT NULL,
    page_path   TEXT,              -- relative wiki path once integrated
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE IF NOT EXISTS links (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    source_path     TEXT    NOT NULL,
    target_path     TEXT    NOT NULL,
    relationship    TEXT    NOT NULL,  -- supports | contradicts | generalises | relates-to
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
