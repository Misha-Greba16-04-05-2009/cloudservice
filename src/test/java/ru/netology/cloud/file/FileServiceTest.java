package ru.netology.cloud.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import ru.netology.cloud.error.BadRequestException;

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

    private final MockMultipartFile file =
            new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

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
    void uploadUsesOriginalNameWhenFilenameMissing() {
        fileService.upload(USER_ID, null, file, null);

        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(files).save(saved.capture());
        assertThat(saved.getValue().getFilename()).isEqualTo("a.txt");
    }

    @Test
    void uploadDuplicateThrowsBadRequest() {
        when(files.existsByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(true);

        assertThatThrownBy(() -> fileService.upload(USER_ID, "a.txt", file, null))
                .isInstanceOf(BadRequestException.class);
        verify(files, never()).save(any());
    }

    @Test
    void uploadEmptyFileThrowsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> fileService.upload(USER_ID, "a.txt", empty, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void invalidFilenamesAreRejected() {
        assertThatThrownBy(() -> fileService.delete(USER_ID, " ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> fileService.delete(USER_ID, "../a.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> fileService.delete(USER_ID, "x".repeat(256))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteMissingFileThrowsBadRequest() {
        when(files.deleteByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(0);

        assertThatThrownBy(() -> fileService.delete(USER_ID, "a.txt")).isInstanceOf(BadRequestException.class);
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
    void downloadMissingFileThrowsBadRequest() {
        when(files.findByUserIdAndFilename(USER_ID, "a.txt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.download(USER_ID, "a.txt")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void renameToExistingNameThrowsBadRequest() {
        when(files.existsByUserIdAndFilename(USER_ID, "b.txt")).thenReturn(true);

        assertThatThrownBy(() -> fileService.rename(USER_ID, "a.txt", "b.txt"))
                .isInstanceOf(BadRequestException.class);
        verify(files, never()).rename(any(), anyString(), anyString());
    }

    @Test
    void renameMissingFileThrowsBadRequest() {
        when(files.rename(USER_ID, "a.txt", "b.txt")).thenReturn(0);

        assertThatThrownBy(() -> fileService.rename(USER_ID, "a.txt", "b.txt"))
                .isInstanceOf(BadRequestException.class);
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
    void listWithNonPositiveLimitThrowsBadRequest() {
        assertThatThrownBy(() -> fileService.list(USER_ID, 0)).isInstanceOf(BadRequestException.class);
    }
}
