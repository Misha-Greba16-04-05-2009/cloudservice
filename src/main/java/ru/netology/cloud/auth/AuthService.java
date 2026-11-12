package ru.netology.cloud.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.netology.cloud.error.BadRequestException;
import ru.netology.cloud.error.UnauthorizedException;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

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
                .orElseThrow(() -> new BadRequestException("Bad credentials"));
        String token = UUID.randomUUID().toString();
        tokens.save(new AuthToken(token, user.getId(), Instant.now()));
        return token;
    }

    @Transactional
    public void logout(String header) {
        String token = stripBearer(header);
        if (token != null) {
            tokens.deleteById(token);
        }
    }

    /** Возвращает id пользователя по заголовку auth-token или бросает 401. */
    @Transactional(readOnly = true)
    public Long authenticate(String header) {
        String token = stripBearer(header);
        if (token == null) {
            throw new UnauthorizedException("Unauthorized error");
        }
        return tokens.findById(token)
                .map(AuthToken::getUserId)
                .orElseThrow(() -> new UnauthorizedException("Unauthorized error"));
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
