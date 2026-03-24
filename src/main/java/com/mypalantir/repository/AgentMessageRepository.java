package com.mypalantir.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveMessage(String conversationId, String role, String content) {
        jdbcTemplate.update(
            "INSERT INTO agent_message(conversation_id, `role`, content, created_at) VALUES (?,?,?,?)",
            conversationId, role, content, LocalDateTime.now()
        );
    }

    public List<ChatMessageDto> listMessages(String conversationId, int limit) {
        return jdbcTemplate.query(
            "SELECT id, `role`, content, created_at FROM agent_message " +
                "WHERE conversation_id = ? ORDER BY id ASC LIMIT ?",
            (rs, rowNum) -> mapMessage(rs),
            conversationId, limit
        );
    }

    private ChatMessageDto mapMessage(ResultSet rs) throws SQLException {
        return new ChatMessageDto(
            rs.getLong("id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant().toEpochMilli()
        );
    }

    public record ChatMessageDto(
        long id,
        String role,
        String content,
        long createdAt
    ) {}
}

