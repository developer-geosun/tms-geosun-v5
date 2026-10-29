package com.geosun.tms.storage.service;

import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.storage.domain.StoredFile;
import com.geosun.tms.storage.dto.StoredFileDto;
import com.geosun.tms.storage.repository.StoredFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Метадані в БД + делегування байтів у {@link StorageService}.
 */
@Service
public class StoredFileService {

  public static final String ADMIN_TEST_DIR = "admin-test";

  private static final long MAX_BYTES = 10L * 1024 * 1024;

  private final StoredFileRepository repository;
  private final StorageService storageService;
  private final CurrentEnterprise currentEnterprise;

  public StoredFileService(
      StoredFileRepository repository,
      StorageService storageService,
      CurrentEnterprise currentEnterprise) {
    this.repository = repository;
    this.storageService = storageService;
    this.currentEnterprise = currentEnterprise;
  }

  public String storageType() {
    return storageService.type();
  }

  @Transactional(readOnly = true)
  public List<StoredFileDto> listAll() {
    return repository.findByEnterpriseIdOrderByCreatedAtDesc(currentEnterprise.requireId()).stream()
        .map(f -> toDto(Objects.requireNonNull(f)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StoredFileDto> listByKeyPrefix(@NonNull String prefix) {
    return repository
        .findByEnterpriseIdAndStorageKeyStartingWithOrderByCreatedAtDesc(
            currentEnterprise.requireId(), prefix)
        .stream()
        .map(f -> toDto(Objects.requireNonNull(f)))
        .toList();
  }

  @Transactional(readOnly = true)
  public StoredFile requireById(@NonNull String id) {
    return Objects.requireNonNull(
        repository
            .findByIdAndEnterpriseId(id, currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Stored file not found")));
  }

  @Transactional(readOnly = true)
  public StoredFileDto getDto(@NonNull String id) {
    return toDto(requireById(id));
  }

  @Transactional(readOnly = true)
  @NonNull
  public OpenedStoredFile open(@NonNull String id) {
    StoredFile file = requireById(id);
    Resource resource = storageService.open(file.getStorageKey());
    return new OpenedStoredFile(file, resource);
  }

  @Transactional
  public StoredFileDto storeMultipart(
      @NonNull MultipartFile multipart,
      @NonNull String relativeDir,
      @Nullable String createdByUserId) {
    if (multipart.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "File is required");
    }
    long size = multipart.getSize();
    if (size <= 0) {
      throw ApiException.badRequest("VALIDATION_ERROR", "File is empty");
    }
    if (size > MAX_BYTES) {
      throw ApiException.badRequest("VALIDATION_ERROR", "File exceeds 10MB limit");
    }
    String originalFilename = multipart.getOriginalFilename();
    if (originalFilename == null || originalFilename.isBlank()) {
      originalFilename = "upload.bin";
    }
    originalFilename = sanitizeOriginalFilename(originalFilename);
    String rawContentType = multipart.getContentType();
    String contentType =
        rawContentType == null || rawContentType.isBlank()
            ? "application/octet-stream"
            : rawContentType;

    String dir = normalizeRelativeDir(relativeDir);
    String storageKey = dir + "/" + UUID.randomUUID() + extensionOf(originalFilename);

    try (InputStream in = multipart.getInputStream()) {
      storageService.put(storageKey, in, size, contentType);
    } catch (IOException ex) {
      throw ApiException.unprocessableEntity(
          "STORAGE_WRITE_FAILED", "Failed to read uploaded file");
    }

    StoredFile entity = new StoredFile();
    entity.setEnterpriseId(currentEnterprise.requireId());
    entity.setStorageKey(storageKey);
    entity.setOriginalFilename(originalFilename);
    entity.setContentType(contentType);
    entity.setSizeBytes(size);
    entity.setCreatedByUserId(createdByUserId);

    try {
      return toDto(repository.save(entity));
    } catch (RuntimeException ex) {
      // Відкат байтів, якщо запис у БД не вдався
      try {
        storageService.delete(storageKey);
      } catch (RuntimeException ignored) {
        // ignore secondary failure
      }
      throw ex;
    }
  }

  @Transactional
  public void delete(@NonNull String id) {
    repository
        .findByIdAndEnterpriseId(id, currentEnterprise.requireId())
        .ifPresent(
            file -> {
              storageService.delete(file.getStorageKey());
              repository.delete(file);
            });
  }

  private StoredFileDto toDto(StoredFile file) {
    return new StoredFileDto(
        file.getId(),
        file.getStorageKey(),
        file.getOriginalFilename(),
        file.getContentType(),
        file.getSizeBytes(),
        file.getCreatedAt(),
        file.getCreatedByUserId());
  }

  static String normalizeRelativeDir(String relativeDir) {
    if (relativeDir == null || relativeDir.isBlank()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "relativeDir is required");
    }
    String normalized = relativeDir.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isBlank()
        || normalized.contains("..")
        || !normalized.matches("[a-zA-Z0-9/_-]+")) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid relativeDir");
    }
    return normalized;
  }

  static String sanitizeOriginalFilename(String name) {
    String base = name.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    base = base.trim();
    if (base.isBlank()) {
      return "upload.bin";
    }
    if (base.length() > 255) {
      base = base.substring(base.length() - 255);
    }
    return base;
  }

  static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    String ext = filename.substring(dot).toLowerCase(Locale.ROOT);
    if (!ext.matches("\\.[a-z0-9]{1,10}")) {
      return "";
    }
    return ext;
  }

  /** Відкритий файл: метадані + ресурс для стрімінгу. */
  public record OpenedStoredFile(StoredFile file, Resource resource) {}
}
