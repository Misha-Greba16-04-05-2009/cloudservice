-- Тестовый пользователь: логин user@example.com, пароль password (хранится BCrypt-хеш)
INSERT INTO users (login, password_hash)
VALUES ('user@example.com', '$2a$10$EnSkbyD1xbp.fj7hvkNO1.tfiBTcaQ6ZV8L48wY6gdB0gHPOMsJSK');
