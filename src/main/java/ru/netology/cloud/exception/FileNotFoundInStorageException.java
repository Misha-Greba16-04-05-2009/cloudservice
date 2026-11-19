package ru.netology.cloud.exception;

/** У пользователя нет файла с таким именем. */
public class FileNotFoundInStorageException extends RuntimeException {
    public FileNotFoundInStorageException(String filename) {
        super("File not found: " + filename);
    }
}
