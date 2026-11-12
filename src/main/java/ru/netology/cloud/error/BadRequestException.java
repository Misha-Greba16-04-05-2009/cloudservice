package ru.netology.cloud.error;

/** Ошибка входных данных: ответ 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
