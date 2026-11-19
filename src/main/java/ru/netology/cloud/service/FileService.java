package ru.netology.cloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import ru.netology.cloud.dto.FileInfo;
import ru.netology.cloud.entity.StoredFile;
import ru.netology.cloud.exception.DuplicateFileException;
import ru.netology.cloud.exception.FileNotFoundInStorageException;
import ru.netology.cloud.exception.InvalidFileDataException;
import ru.netology.cloud.repository.FileRepository;

import java.time.Instant;
import java.util.List;

/** Бизнес-логика работы с файлами. Ничего не знает о HTTP: получает готовые байты и имена. */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final int MAX_FILENAME_LENGTH = 255;

    private final FileRepository files;

    public FileService(FileRepository files) {
        this.files = files;
    }

    @Transactional
    public void upload(Long userId, String filename, byte[] content, String hash) {
        if (content == null || content.length == 0) {
            throw new InvalidFileDataException("File is empty");
        }
        validateFilename(filename);
        if (files.existsByUserIdAndFilename(userId, filename)) {
            throw new DuplicateFileException(filename);
        }
        // Фронт hash не шлёт: тогда считаем MD5 сами
        String fileHash = hash != null && !hash.isBlank() ? hash : DigestUtils.md5DigestAsHex(content);
        files.save(new StoredFile(userId, filename, fileHash, content, Instant.now()));
        log.info("User id={} uploaded '{}' ({} bytes)", userId, filename, content.length);
    }

    @Transactional
    public void delete(Long userId, String filename) {
        validateFilename(filename);
        if (files.deleteByUserIdAndFilename(userId, filename) == 0) {
            throw new FileNotFoundInStorageException(filename);
        }
        log.info("User id={} deleted '{}'", userId, filename);
    }

    @Transactional(readOnly = true)
    public StoredFile download(Long userId, String filename) {
        validateFilename(filename);
        StoredFile file = files.findByUserIdAndFilename(userId, filename)
                .orElseThrow(() -> new FileNotFoundInStorageException(filename));
        log.info("User id={} downloaded '{}'", userId, filename);
        return file;
    }

    @Transactional
    public void rename(Long userId, String filename, String newName) {
        validateFilename(filename);
        validateFilename(newName);
        if (filename.equals(newName)) {
            return;
        }
        if (files.existsByUserIdAndFilename(userId, newName)) {
            throw new DuplicateFileException(newName);
        }
        if (files.rename(userId, filename, newName) == 0) {
            throw new FileNotFoundInStorageException(filename);
        }
        log.info("User id={} renamed '{}' to '{}'", userId, filename, newName);
    }

    @Transactional(readOnly = true)
    public List<FileInfo> list(Long userId, Integer limit) {
        if (limit != null && limit <= 0) {
            throw new InvalidFileDataException("Limit must be positive");
        }
        Pageable page = limit == null ? Pageable.unpaged() : Pageable.ofSize(limit);
        List<FileInfo> result = files.findInfoByUserId(userId, page);
        log.debug("User id={} listed {} file(s), limit={}", userId, result.size(), limit);
        return result;
    }

    static void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidFileDataException("Filename is empty");
        }
        if (filename.length() > MAX_FILENAME_LENGTH || filename.contains("/") || filename.contains("\\")) {
            throw new InvalidFileDataException("Invalid filename");
        }
    }
}
