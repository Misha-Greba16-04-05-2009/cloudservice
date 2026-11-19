package ru.netology.cloud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Активный токен. Logout удаляет строку, после этого токен даёт 401. */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthToken() {
    }

    public AuthToken(String token, Long userId, Instant createdAt) {
        this.token = token;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
