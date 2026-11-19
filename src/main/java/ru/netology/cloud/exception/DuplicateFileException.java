package ru.netology.cloud.exception;

/** У пользователя уже есть файл с таким именем. */
public class DuplicateFileException extends RuntimeException {
    public DuplicateFileException(String filename) {
        super("File already exists: " + filename);
    }
}
