package ru.netology.cloud.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Фронт читает именно поле "auth-token". */
public record LoginResponse(@JsonProperty("auth-token") String authToken) {
}
