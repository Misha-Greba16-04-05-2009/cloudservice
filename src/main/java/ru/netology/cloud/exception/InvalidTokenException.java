package ru.netology.cloud.exception;

/** Токен не передан, не найден или уже отозван через logout. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Unauthorized error");
    }
}
