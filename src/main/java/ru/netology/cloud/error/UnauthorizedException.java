package ru.netology.cloud.error;

/** Нет или неверный auth-token: ответ 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
