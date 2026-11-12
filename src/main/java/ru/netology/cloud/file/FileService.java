package ru.netology.cloud.file;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.netology.cloud.error.BadRequestException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

@Service
public class FileService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final FileRepository files;

    public FileService(FileRepository files) {
        this.files = files;
    }

    @Transactional
    public void upload(Long userId, String filename, MultipartFile file, String hash) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String name = filename != null ? filename : file.getOriginalFilename();
        validateFilename(name);
        if (files.existsByUserIdAndFilename(userId, name)) {
            throw new BadRequestException("File already exists");
        }
        byte[] content = readBytes(file);
        // Фронт hash не шлёт: тогда считаем MD5 сами
        String fileHash = hash != null && !hash.isBlank() ? hash : DigestUtils.md5DigestAsHex(content);
        files.save(new StoredFile(userId, name, fileHash, content, Instant.now()));
    }

    @Transactional
    public void delete(Long userId, String filename) {
        validateFilename(filename);
        if (files.deleteByUserIdAndFilename(userId, filename) == 0) {
            throw new BadRequestException("File not found");
        }
    }

    @Transactional(readOnly = true)
    public StoredFile download(Long userId, String filename) {
        validateFilename(filename);
        return files.findByUserIdAndFilename(userId, filename)
                .orElseThrow(() -> new BadRequestException("File not found"));
    }

    @Transactional
    public void rename(Long userId, String filename, String newName) {
        validateFilename(filename);
        validateFilename(newName);
        if (filename.equals(newName)) {
            return;
        }
        if (files.existsByUserIdAndFilename(userId, newName)) {
            throw new BadRequestException("File with new name already exists");
        }
        if (files.rename(userId, filename, newName) == 0) {
            throw new BadRequestException("File not found");
        }
    }

    @Transactional(readOnly = true)
    public List<FileInfo> list(Long userId, Integer limit) {
        if (limit != null && limit <= 0) {
            throw new BadRequestException("Limit must be positive");
        }
        Pageable page = limit == null ? Pageable.unpaged() : Pageable.ofSize(limit);
        return files.findInfoByUserId(userId, page);
    }

    static void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Filename is empty");
        }
        if (filename.length() > MAX_FILENAME_LENGTH || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Invalid filename");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read uploaded file", e);
        }
    }
}
