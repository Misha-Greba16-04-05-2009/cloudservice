CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    login         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE auth_tokens
(
    token      VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_tokens_user_id ON auth_tokens (user_id);

CREATE TABLE files
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename   VARCHAR(255) NOT NULL,
    size       BIGINT       NOT NULL,
    hash       VARCHAR(255),
    content    BYTEA        NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE  NOT NULL,
    CONSTRAINT uq_files_user_filename UNIQUE (user_id, filename)
);
