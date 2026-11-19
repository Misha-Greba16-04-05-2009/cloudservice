package ru.netology.cloud.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ru.netology.cloud.dto.FileInfo;
import ru.netology.cloud.entity.StoredFile;
import ru.netology.cloud.exception.DuplicateFileException;
import ru.netology.cloud.exception.FileNotFoundInStorageException;
import ru.netology.cloud.exception.InvalidFileDataException;
import ru.netology.cloud.repository.FileRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private FileRepository files;

    @InjectMocks
    private FileService fileService;

    private final byte[] file = "hello".getBytes();

    @Test
    void uploadSavesFileWithSizeAndComputedHash() {
        when(files.existsByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(false);

        fileService.upload(USER_ID, "a.txt", file, null);

        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(files).save(saved.capture());
        assertThat(saved.getValue().getFilename()).isEqualTo("a.txt");
        assertThat(saved.getValue().getSize()).isEqualTo(5);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getHash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void uploadKeepsClientHash() {
        fileService.upload(USER_ID, "a.txt", file, "client-hash");

        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(files).save(saved.capture());
        assertThat(saved.getValue().getHash()).isEqualTo("client-hash");
    }

    @Test
    void uploadDuplicateThrowsDuplicateFile() {
        when(files.existsByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(true);

        assertThatThrownBy(() -> fileService.upload(USER_ID, "a.txt", file, null))
                .isInstanceOf(DuplicateFileException.class);
        verify(files, never()).save(any());
    }

    @Test
    void uploadEmptyFileThrowsInvalidData() {
        assertThatThrownBy(() -> fileService.upload(USER_ID, "a.txt", new byte[0], null))
                .isInstanceOf(InvalidFileDataException.class);
    }

    @Test
    void invalidFilenamesAreRejected() {
        assertThatThrownBy(() -> fileService.delete(USER_ID, " ")).isInstanceOf(InvalidFileDataException.class);
        assertThatThrownBy(() -> fileService.delete(USER_ID, "../a.txt")).isInstanceOf(InvalidFileDataException.class);
        assertThatThrownBy(() -> fileService.delete(USER_ID, "x".repeat(256)))
                .isInstanceOf(InvalidFileDataException.class);
        assertThatThrownBy(() -> fileService.upload(USER_ID, null, file, null))
                .isInstanceOf(InvalidFileDataException.class);
    }

    @Test
    void deleteMissingFileThrowsNotFound() {
        when(files.deleteByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(0);

        assertThatThrownBy(() -> fileService.delete(USER_ID, "a.txt")).isInstanceOf(FileNotFoundInStorageException.class);
    }

    @Test
    void deleteExistingFile() {
        when(files.deleteByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(1);

        fileService.delete(USER_ID, "a.txt");

        verify(files).deleteByUserIdAndFilename(USER_ID, "a.txt");
    }

    @Test
    void downloadReturnsOwnFile() {
        StoredFile stored = new StoredFile(USER_ID, "a.txt", "h", "hello".getBytes(), Instant.now());
        when(files.findByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(Optional.of(stored));

        assertThat(fileService.download(USER_ID, "a.txt")).isSameAs(stored);
    }

    @Test
    void downloadMissingFileThrowsNotFound() {
        when(files.findByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.download(USER_ID, "a.txt")).isInstanceOf(FileNotFoundInStorageException.class);
    }

    @Test
    void renameToExistingNameThrowsDuplicate() {
        when(files.existsByUserIdAndFilename(USER_ID, "b.txt")).thenReturn(true);

        assertThatThrownBy(() -> fileService.rename(USER_ID, "a.txt", "b.txt"))
                .isInstanceOf(DuplicateFileException.class);
        verify(files, never()).rename(any(), anyString(), anyString());
    }

    @Test
    void renameMissingFileThrowsNotFound() {
        when(files.rename(USER_ID, "a.txt", "b.txt")).thenReturn(0);

        assertThatThrownBy(() -> fileService.rename(USER_ID, "a.txt", "b.txt"))
                .isInstanceOf(FileNotFoundInStorageException.class);
    }

    @Test
    void renameUpdatesName() {
        when(files.rename(USER_ID, "a.txt", "b.txt")).thenReturn(1);

        fileService.rename(USER_ID, "a.txt", "b.txt");

        verify(files).rename(USER_ID, "a.txt", "b.txt");
    }

    @Test
    void listAppliesLimit() {
        List<FileInfo> expected = List.of(new FileInfo("a.txt", 5));
        when(files.findInfoByUserId(USER_ID, Pageable.ofSize(3))).thenReturn(expected);

        assertThat(fileService.list(USER_ID, 3)).isEqualTo(expected);
    }

    @Test
    void listWithoutLimitReturnsAll() {
        when(files.findInfoByUserId(USER_ID, Pageable.unpaged())).thenReturn(List.of());

        assertThat(fileService.list(USER_ID, null)).isEmpty();
    }

    @Test
    void listWithNonPositiveLimitThrowsInvalidData() {
        assertThatThrownBy(() -> fileService.list(USER_ID, 0)).isInstanceOf(InvalidFileDataException.class);
    }
}
