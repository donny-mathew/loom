package com.loom.index;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class SqliteIndexStore {

    private final JdbcTemplate jdbc;

    public SqliteIndexStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Findings ────────────────────────────────────────────────────────────

    public long insertFinding(String sessionId, String type, String title, String body, String pagePath) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO findings (session_id, type, title, body, page_path) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, body);
            ps.setString(5, pagePath);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public List<Finding> allFindings() {
        return jdbc.query("SELECT * FROM findings ORDER BY id", findingMapper());
    }

    public void updateFindingPagePath(long id, String pagePath) {
        jdbc.update("UPDATE findings SET page_path = ? WHERE id = ?", pagePath, id);
    }

    public void deleteAllFindings() {
        jdbc.update("DELETE FROM findings");
    }

    // ── Links ────────────────────────────────────────────────────────────────

    public long insertLink(String sourcePath, String targetPath, String relationship) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO links (source_path, target_path, relationship) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sourcePath);
            ps.setString(2, targetPath);
            ps.setString(3, relationship);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public List<Link> allLinks() {
        return jdbc.query("SELECT * FROM links ORDER BY id", linkMapper());
    }

    public void deleteAllLinks() {
        jdbc.update("DELETE FROM links");
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private RowMapper<Finding> findingMapper() {
        return (rs, row) -> new Finding(
                rs.getLong("id"),
                rs.getString("session_id"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("page_path"),
                rs.getString("created_at")
        );
    }

    private RowMapper<Link> linkMapper() {
        return (rs, row) -> new Link(
                rs.getLong("id"),
                rs.getString("source_path"),
                rs.getString("target_path"),
                rs.getString("relationship"),
                rs.getString("created_at")
        );
    }
}
