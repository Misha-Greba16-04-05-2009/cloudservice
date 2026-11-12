package ru.netology.cloud.error;

/** Тело ошибки по спецификации: {message, id}. */
public record ErrorResponse(String message, int id) {
}
