package ru.netology.cloud.file;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<StoredFile, Long> {

    boolean existsByUserIdAndFilename(Long userId, String filename);

    Optional<StoredFile> findByUserIdAndFilename(Long userId, String filename);

    /** Только имя и размер, без чтения содержимого. */
    @Query("select new ru.netology.cloud.file.FileInfo(f.filename, f.size) "
            + "from StoredFile f where f.userId = :userId order by f.filename")
    List<FileInfo> findInfoByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query("delete from StoredFile f where f.userId = :userId and f.filename = :filename")
    int deleteByUserIdAndFilename(Long userId, String filename);

    @Modifying
    @Query("update StoredFile f set f.filename = :newName where f.userId = :userId and f.filename = :filename")
    int rename(Long userId, String filename, String newName);
}
