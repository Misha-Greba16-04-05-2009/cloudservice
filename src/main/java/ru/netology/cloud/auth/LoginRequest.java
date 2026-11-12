package ru.netology.cloud.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String login, @NotBlank String password) {
}
