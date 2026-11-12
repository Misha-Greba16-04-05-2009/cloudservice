package ru.netology.cloud.file;

/** Элемент ответа /list. */
public record FileInfo(String filename, long size) {
}
