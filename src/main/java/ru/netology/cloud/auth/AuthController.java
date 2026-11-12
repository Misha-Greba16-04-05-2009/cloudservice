package ru.netology.cloud.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public static final String AUTH_HEADER = "auth-token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(request.login(), request.password()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = AUTH_HEADER, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.ok().build();
    }
}
