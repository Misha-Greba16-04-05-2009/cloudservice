package ru.netology.cloud.exception;

/** Некорректные данные файла: пустое содержимое, недопустимое имя, неверный лимит списка. */
public class InvalidFileDataException extends RuntimeException {
    public InvalidFileDataException(String message) {
        super(message);
    }
}
