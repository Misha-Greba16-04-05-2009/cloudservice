package ru.netology.cloud.dto;

/** Тело ошибки по спецификации: {message, id}. */
public record ErrorResponse(String message, int id) {
}
