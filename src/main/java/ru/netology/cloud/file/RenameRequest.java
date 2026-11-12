package ru.netology.cloud.file;

/**
 * Тело PUT /file. По спецификации поле называется "name",
 * но фронт netology-diplom-frontend шлёт "filename", поэтому принимаем оба.
 */
public record RenameRequest(String name, String filename) {

    public String newName() {
        return name != null && !name.isBlank() ? name : filename;
    }
}
