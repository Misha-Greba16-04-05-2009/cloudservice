package ru.netology.cloud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Файл пользователя. Содержимое лежит в колонке bytea. */
@Entity
@Table(name = "files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private long size;

    private String hash;

    // Читается только при скачивании: /list, rename и delete идут отдельными запросами без content
    @Column(nullable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredFile() {
    }

    public StoredFile(Long userId, String filename, String hash, byte[] content, Instant createdAt) {
        this.userId = userId;
        this.filename = filename;
        this.size = content.length;
        this.hash = hash;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public String getHash() {
        return hash;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
