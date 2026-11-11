package com.scantaker.api.repository;

import com.scantaker.api.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByFilename(String filename);
    boolean existsByFilename(String filename);
}
