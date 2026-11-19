package ru.netology.cloud.exception;

/** Неверный логин или пароль. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Bad credentials");
    }
}
