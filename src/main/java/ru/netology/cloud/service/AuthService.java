package ru.netology.cloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.netology.cloud.entity.AuthToken;
import ru.netology.cloud.entity.User;
import ru.netology.cloud.exception.InvalidCredentialsException;
import ru.netology.cloud.exception.InvalidTokenException;
import ru.netology.cloud.repository.AuthTokenRepository;
import ru.netology.cloud.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Фронт шлёт заголовок в виде "Bearer <token>". */
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, AuthTokenRepository tokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String login(String login, String password) {
        User user = users.findByLogin(login)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for '{}'", login);
                    return new InvalidCredentialsException();
                });
        String token = UUID.randomUUID().toString();
        tokens.save(new AuthToken(token, user.getId(), Instant.now()));
        log.info("User '{}' (id={}) logged in", login, user.getId());
        return token;
    }

    @Transactional
    public void logout(String header) {
        String token = stripBearer(header);
        if (token == null) {
            log.debug("Logout without token");
            return;
        }
        tokens.findById(token).ifPresent(t -> {
            tokens.delete(t);
            log.info("User id={} logged out", t.getUserId());
        });
    }

    /** Возвращает id пользователя по токену из заголовка auth-token. */
    @Transactional(readOnly = true)
    public Long authenticate(String header) {
        String token = stripBearer(header);
        if (token == null) {
            log.debug("Request without auth token");
            throw new InvalidTokenException();
        }
        return tokens.findById(token)
                .map(AuthToken::getUserId)
                .orElseThrow(() -> {
                    log.debug("Unknown or revoked auth token");
                    return new InvalidTokenException();
                });
    }

    static String stripBearer(String header) {
        if (header == null) {
            return null;
        }
        String token = header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
        token = token.trim();
        return token.isEmpty() ? null : token;
    }
}
