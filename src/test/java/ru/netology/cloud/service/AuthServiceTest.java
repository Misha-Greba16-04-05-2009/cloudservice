package ru.netology.cloud.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.netology.cloud.entity.AuthToken;
import ru.netology.cloud.entity.User;
import ru.netology.cloud.exception.InvalidCredentialsException;
import ru.netology.cloud.exception.InvalidTokenException;
import ru.netology.cloud.repository.AuthTokenRepository;
import ru.netology.cloud.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private AuthTokenRepository tokens;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final User user = new User(1L, "user@example.com", "hash");

    @Test
    void loginReturnsNewTokenAndStoresIt() {
        when(users.findByLogin("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        String token = authService.login("user@example.com", "password");

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokens).save(saved.capture());
        assertThat(token).isNotBlank();
        assertThat(saved.getValue().getToken()).isEqualTo(token);
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void loginWithWrongPasswordThrowsBadRequest() {
        when(users.findByLogin("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).save(any());
    }

    @Test
    void loginWithUnknownUserThrowsBadRequest() {
        when(users.findByLogin("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody", "password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void authenticateAcceptsBearerPrefix() {
        when(tokens.findById("abc")).thenReturn(Optional.of(new AuthToken("abc", 1L, Instant.now())));

        assertThat(authService.authenticate("Bearer abc")).isEqualTo(1L);
        assertThat(authService.authenticate("abc")).isEqualTo(1L);
    }

    @Test
    void authenticateWithUnknownTokenThrowsUnauthorized() {
        when(tokens.findById("abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate("Bearer abc"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void authenticateWithoutHeaderThrowsUnauthorized() {
        assertThatThrownBy(() -> authService.authenticate(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> authService.authenticate("Bearer ")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void logoutDeletesToken() {
        AuthToken token = new AuthToken("abc", 1L, Instant.now());
        when(tokens.findById("abc")).thenReturn(Optional.of(token));

        authService.logout("Bearer abc");

        verify(tokens).delete(token);
    }

    @Test
    void logoutWithUnknownTokenDoesNothing() {
        when(tokens.findById("abc")).thenReturn(Optional.empty());

        authService.logout("Bearer abc");

        verify(tokens, never()).delete(any());
    }

    @Test
    void logoutWithoutHeaderDoesNothing() {
        authService.logout(null);

        verify(tokens, never()).findById(any());
    }
}
