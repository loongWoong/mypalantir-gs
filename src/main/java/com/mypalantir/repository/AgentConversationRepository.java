package com.mypalantir.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createConversation(String id, String userId, String title) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
            "INSERT INTO agent_conversation(id, user_id, title, created_at, updated_at, status) VALUES (?,?,?,?,?,?)",
            id, userId, title, now, now, "active"
        );
    }

    public void updateConversationPreview(String id, String preview) {
        jdbcTemplate.update(
            "UPDATE agent_conversation SET last_preview = ?, updated_at = ? WHERE id = ?",
            preview, LocalDateTime.now(), id
        );
    }

    /**
     * 如果当前标题还是默认值（null / 空串 / “新对话”），则用首条用户问题更新标题。
     */
    public void updateTitleIfDefault(String id, String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            return;
        }
        String truncated = newTitle.length() > 50 ? newTitle.substring(0, 50) : newTitle;
        jdbcTemplate.update(
            "UPDATE agent_conversation SET title = ?, updated_at = ? " +
                "WHERE id = ? AND (title IS NULL OR title = '' OR title = '新对话')",
            truncated, LocalDateTime.now(), id
        );
    }

    public List<ConversationSummary> listByUser(String userId, int limit, int offset) {
        return jdbcTemplate.query(
            "SELECT id, title, last_preview, created_at, updated_at, status " +
                "FROM agent_conversation WHERE user_id = ? AND status <> 'deleted' " +
                "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> mapSummary(rs),
            userId, limit, offset
        );
    }

    private ConversationSummary mapSummary(ResultSet rs) throws SQLException {
        return new ConversationSummary(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("last_preview"),
            rs.getTimestamp("created_at").toInstant().toEpochMilli(),
            rs.getTimestamp("updated_at").toInstant().toEpochMilli(),
            rs.getString("status")
        );
    }

    public record ConversationSummary(
        String id,
        String title,
        String lastPreview,
        long createdAt,
        long updatedAt,
        String status
    ) {}
}

