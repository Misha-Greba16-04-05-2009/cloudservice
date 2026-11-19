package ru.netology.cloud.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.netology.cloud.config.AuthInterceptor;
import ru.netology.cloud.dto.FileInfo;
import ru.netology.cloud.dto.RenameRequest;
import ru.netology.cloud.entity.StoredFile;
import ru.netology.cloud.service.FileService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Все методы защищены AuthInterceptor: userId берётся из проверенного auth-token. */
@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(path = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                       @RequestParam(required = false) String filename,
                                       @RequestPart("file") MultipartFile file,
                                       @RequestParam(required = false) String hash) throws IOException {
        String name = filename != null ? filename : file.getOriginalFilename();
        fileService.upload(userId, name, file.getBytes(), hash);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/file")
    public ResponseEntity<Void> delete(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                       @RequestParam String filename) {
        fileService.delete(userId, filename);
        return ResponseEntity.ok().build();
    }

    /** Отдаём байты файла: фронт читает ответ как blob и сохраняет как есть. */
    @GetMapping("/file")
    public ResponseEntity<byte[]> download(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                           @RequestParam String filename) {
        StoredFile file = fileService.download(userId, filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFilename(), StandardCharsets.UTF_8).build().toString())
                .body(file.getContent());
    }

    @PutMapping("/file")
    public ResponseEntity<Void> rename(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                       @RequestParam String filename,
                                       @RequestBody RenameRequest request) {
        fileService.rename(userId, filename, request.newName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/list")
    public List<FileInfo> list(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                               @RequestParam(required = false) Integer limit) {
        return fileService.list(userId, limit);
    }
}
