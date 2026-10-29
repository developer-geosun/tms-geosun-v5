package com.geosun.tms.reference.service;

import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.reference.domain.DocumentTypeFieldDefinition;
import com.geosun.tms.reference.domain.DocumentTypeListView;
import com.geosun.tms.reference.domain.DocumentTypeReference;
import com.geosun.tms.reference.domain.DocumentTypeScanPage;
import com.geosun.tms.reference.dto.request.CreateDocumentTypeRequest;
import com.geosun.tms.reference.dto.request.DocumentTypeFieldDefinitionRequest;
import com.geosun.tms.reference.dto.request.DocumentTypeScanPageRequest;
import com.geosun.tms.reference.dto.request.UpdateDocumentTypeRequest;
import com.geosun.tms.reference.dto.response.DocumentTypeFieldDefinitionDto;
import com.geosun.tms.reference.dto.response.DocumentTypeReferenceDto;
import com.geosun.tms.reference.dto.response.DocumentTypeScanPageDto;
import com.geosun.tms.reference.repository.CountryReferenceRepository;
import com.geosun.tms.reference.repository.DocumentTypeReferenceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentTypeReferenceService {

  private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

  private final DocumentTypeReferenceRepository documentTypeRepository;
  private final CountryReferenceRepository countryReferenceRepository;
  private final CurrentEnterprise currentEnterprise;

  public DocumentTypeReferenceService(
      DocumentTypeReferenceRepository documentTypeRepository,
      CountryReferenceRepository countryReferenceRepository,
      CurrentEnterprise currentEnterprise) {
    this.documentTypeRepository = documentTypeRepository;
    this.countryReferenceRepository = countryReferenceRepository;
    this.currentEnterprise = currentEnterprise;
  }

  /**
   * Копіює каталог типів документів у нове підприємство з новими id. Джерело — перше за датою
   * підприємство (його поточний вміст); якщо там порожньо — канонічні рядки c2000000-….
   *
   * @return кількість скопійованих рядків
   */
  @Transactional
  public int copyCatalogToEnterprise(
      @NonNull String targetEnterpriseId, String sourceEnterpriseId) {
    List<DocumentTypeReference> source =
        sourceEnterpriseId == null
            ? List.of()
            : documentTypeRepository
                .findByEnterpriseIdAndDeletedFalseOrderByCountryCodeAscNameUkAsc(
                    sourceEnterpriseId);
    if (source.isEmpty()) {
      source = documentTypeRepository.findCanonicalActive();
    }
    List<DocumentTypeReference> copies = new ArrayList<>();
    for (DocumentTypeReference row : source) {
      DocumentTypeReference copy = new DocumentTypeReference();
      copy.setEnterpriseId(targetEnterpriseId);
      copy.setNameUk(row.getNameUk());
      copy.setNameEn(row.getNameEn());
      copy.setNameRu(row.getNameRu());
      copy.setCountryCode(row.getCountryCode());
      copy.setPlannedScanPages(new ArrayList<>(row.getPlannedScanPages()));
      copy.setComment(row.getComment() == null ? "" : row.getComment());
      copy.setFieldDefinitions(new ArrayList<>(row.getFieldDefinitions()));
      copies.add(copy);
    }
    documentTypeRepository.saveAll(copies);
    return copies.size();
  }

  @Transactional(readOnly = true)
  public List<DocumentTypeReferenceDto> list(
      DocumentTypeListView view, String search, String country) {
    String normalizedSearch = normalizeSearch(search);
    String normalizedCountry = normalizeCountryFilter(country);
    DocumentTypeListView effectiveView = view == null ? DocumentTypeListView.ACTIVE : view;
    String enterpriseId = currentEnterprise.requireId();
    List<DocumentTypeReference> rows =
        switch (effectiveView) {
          case ACTIVE ->
              documentTypeRepository.searchActive(
                  enterpriseId, normalizedSearch, normalizedCountry);
          case DELETED ->
              documentTypeRepository.searchDeleted(
                  enterpriseId, normalizedSearch, normalizedCountry);
          case ALL ->
              documentTypeRepository.searchAll(enterpriseId, normalizedSearch, normalizedCountry);
        };
    return rows.stream().map(row -> toDto(Objects.requireNonNull(row))).toList();
  }

  @Transactional(readOnly = true)
  public DocumentTypeReferenceDto getById(@NonNull String id) {
    DocumentTypeReference row = requireDocumentType(id);
    return toDto(row);
  }

  @Transactional
  public DocumentTypeReferenceDto create(@NonNull CreateDocumentTypeRequest request) {
    String countryCode = normalizeCountryCode(request.countryCode());
    assertCountryExists(countryCode);
    String nameUk = normalizeName(request.nameUk());
    assertUniqueName(countryCode, nameUk, null);

    DocumentTypeReference row = new DocumentTypeReference();
    row.setEnterpriseId(currentEnterprise.requireId());
    applyFields(
        row,
        nameUk,
        normalizeName(request.nameEn()),
        normalizeName(request.nameRu()),
        countryCode,
        normalizeScanPages(request.plannedScanPages()),
        normalizeComment(request.comment()),
        normalizeFieldDefinitions(request.fieldDefinitions()));
    DocumentTypeReference saved =
        Objects.requireNonNull(documentTypeRepository.save(Objects.requireNonNull(row)));
    return toDto(saved);
  }

  @Transactional
  public DocumentTypeReferenceDto update(
      @NonNull String id, @NonNull UpdateDocumentTypeRequest request) {
    DocumentTypeReference row = requireActiveDocumentType(id);
    String countryCode = normalizeCountryCode(request.countryCode());
    assertCountryExists(countryCode);
    String nameUk = normalizeName(request.nameUk());
    assertUniqueName(countryCode, nameUk, id);

    applyFields(
        row,
        nameUk,
        normalizeName(request.nameEn()),
        normalizeName(request.nameRu()),
        countryCode,
        normalizeScanPages(request.plannedScanPages()),
        normalizeComment(request.comment()),
        normalizeFieldDefinitions(request.fieldDefinitions()));
    DocumentTypeReference saved =
        Objects.requireNonNull(documentTypeRepository.save(Objects.requireNonNull(row)));
    return toDto(saved);
  }

  @Transactional
  public void softDelete(@NonNull String id) {
    DocumentTypeReference row = requireDocumentType(id);
    if (row.isDeleted()) {
      return;
    }
    row.setDeleted(true);
    row.setDeletedAt(Instant.now());
    documentTypeRepository.save(row);
  }

  @Transactional
  public DocumentTypeReferenceDto restore(@NonNull String id) {
    DocumentTypeReference row = requireDocumentType(id);
    if (!row.isDeleted()) {
      return toDto(row);
    }
    assertUniqueName(
        Objects.requireNonNull(row.getCountryCode()), Objects.requireNonNull(row.getNameUk()), id);
    row.setDeleted(false);
    row.setDeletedAt(null);
    DocumentTypeReference saved = Objects.requireNonNull(documentTypeRepository.save(row));
    return toDto(saved);
  }

  private void applyFields(
      DocumentTypeReference row,
      String nameUk,
      String nameEn,
      String nameRu,
      String countryCode,
      List<DocumentTypeScanPage> plannedScanPages,
      String comment,
      List<DocumentTypeFieldDefinition> fieldDefinitions) {
    row.setNameUk(nameUk);
    row.setNameEn(nameEn);
    row.setNameRu(nameRu);
    row.setCountryCode(countryCode);
    row.setPlannedScanPages(plannedScanPages);
    row.setComment(comment);
    row.setFieldDefinitions(fieldDefinitions);
  }

  private List<DocumentTypeScanPage> normalizeScanPages(List<DocumentTypeScanPageRequest> raw) {
    if (raw == null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "plannedScanPages is required");
    }
    Set<String> seenKeys = new HashSet<>();
    List<DocumentTypeScanPage> normalized = new ArrayList<>();
    for (DocumentTypeScanPageRequest item : raw) {
      String key = normalizeScanPageKey(item.key());
      if (!seenKeys.add(key.toLowerCase(Locale.ROOT))) {
        throw ApiException.badRequest(
            "DOCUMENT_TYPE_SCAN_PAGE_KEY_DUPLICATE",
            "Duplicate scan page key in plannedScanPages: " + key);
      }
      normalized.add(
          new DocumentTypeScanPage(
              key,
              normalizeLegend(item.legendEn()),
              normalizeLegend(item.legendUa()),
              normalizeLegend(item.legendRu())));
    }
    return normalized;
  }

  private static String normalizeScanPageKey(String key) {
    if (key == null || key.isBlank()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "plannedScanPages[].key is required");
    }
    return key.trim();
  }

  private static String normalizeLegend(String value) {
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "plannedScanPages[].legend is required");
    }
    return value.trim();
  }

  private static String normalizeComment(String comment) {
    if (comment == null) {
      return "";
    }
    String trimmed = comment.trim();
    if (trimmed.length() > 512) {
      throw ApiException.badRequest("VALIDATION_ERROR", "comment must be at most 512 characters");
    }
    return trimmed;
  }

  private List<DocumentTypeFieldDefinition> normalizeFieldDefinitions(
      List<DocumentTypeFieldDefinitionRequest> raw) {
    if (raw == null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "fieldDefinitions is required");
    }
    if (raw.isEmpty()) {
      throw ApiException.badRequest(
          "DOCUMENT_TYPE_FIELDS_EMPTY", "fieldDefinitions must not be empty");
    }
    Set<String> seenKeys = new HashSet<>();
    List<DocumentTypeFieldDefinition> normalized = new ArrayList<>();
    boolean hasRequired = false;
    for (DocumentTypeFieldDefinitionRequest item : raw) {
      String key = normalizeFieldKey(item.key());
      if (!seenKeys.add(key.toLowerCase(Locale.ROOT))) {
        throw ApiException.badRequest(
            "VALIDATION_ERROR", "Duplicate field key in fieldDefinitions: " + key);
      }
      if (item.required() == null) {
        throw ApiException.badRequest(
            "VALIDATION_ERROR", "fieldDefinitions[].required is required");
      }
      boolean required = item.required();
      if (required) {
        hasRequired = true;
      }
      normalized.add(
          new DocumentTypeFieldDefinition(
              key,
              normalizeName(item.nameUk()),
              normalizeName(item.nameEn()),
              normalizeName(item.nameRu()),
              required));
    }
    if (!hasRequired) {
      throw ApiException.badRequest(
          "DOCUMENT_TYPE_NO_REQUIRED_FIELD",
          "At least one fieldDefinitions[].required must be true");
    }
    return normalized;
  }

  private static String normalizeFieldKey(String key) {
    if (key == null || key.isBlank()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "fieldDefinitions[].key is required");
    }
    String trimmed = key.trim();
    if (!FIELD_KEY_PATTERN.matcher(trimmed).matches()) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR",
          "fieldDefinitions[].key must match [a-zA-Z][a-zA-Z0-9_]*, got: " + trimmed);
    }
    return trimmed;
  }

  private void assertCountryExists(String countryCode) {
    if (!countryReferenceRepository.existsById(Objects.requireNonNull(countryCode))) {
      throw ApiException.badRequest("COUNTRY_NOT_FOUND", "Country not found: " + countryCode);
    }
  }

  private void assertUniqueName(String countryCode, String nameUk, String excludeId) {
    String enterpriseId = currentEnterprise.requireId();
    boolean exists =
        excludeId == null
            ? documentTypeRepository
                .existsByEnterpriseIdAndCountryCodeIgnoreCaseAndNameUkIgnoreCaseAndDeletedFalse(
                    enterpriseId, countryCode, nameUk)
            : documentTypeRepository
                .existsByEnterpriseIdAndCountryCodeIgnoreCaseAndNameUkIgnoreCaseAndDeletedFalseAndIdNot(
                    enterpriseId, countryCode, nameUk, excludeId);
    if (exists) {
      throw ApiException.conflict(
          "DOCUMENT_TYPE_NAME_EXISTS",
          "Active document type with this name already exists for country " + countryCode);
    }
  }

  private DocumentTypeReference requireDocumentType(@NonNull String id) {
    return documentTypeRepository
        .findByIdAndEnterpriseId(Objects.requireNonNull(id), currentEnterprise.requireId())
        .orElseThrow(() -> ApiException.notFound("Document type not found"));
  }

  private DocumentTypeReference requireActiveDocumentType(@NonNull String id) {
    DocumentTypeReference row = requireDocumentType(Objects.requireNonNull(id));
    if (row.isDeleted()) {
      throw ApiException.conflict("DOCUMENT_TYPE_DELETED", "Document type is deleted");
    }
    return row;
  }

  private DocumentTypeReferenceDto toDto(DocumentTypeReference row) {
    List<DocumentTypeScanPageDto> pages =
        row.getPlannedScanPages().stream()
            .map(
                p -> new DocumentTypeScanPageDto(p.key(), p.legendEn(), p.legendUa(), p.legendRu()))
            .toList();
    List<DocumentTypeFieldDefinitionDto> fields =
        row.getFieldDefinitions().stream()
            .map(
                f ->
                    new DocumentTypeFieldDefinitionDto(
                        f.key(), f.nameUk(), f.nameEn(), f.nameRu(), f.required()))
            .toList();
    return new DocumentTypeReferenceDto(
        Objects.requireNonNull(row.getId()),
        row.getNameUk(),
        row.getNameEn(),
        row.getNameRu(),
        row.getCountryCode(),
        pages,
        row.getComment() == null ? "" : row.getComment(),
        fields,
        row.isDeleted(),
        row.getDeletedAt(),
        row.getCreatedAt(),
        row.getUpdatedAt());
  }

  private static String normalizeName(String value) {
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Name is required");
    }
    return value.trim();
  }

  private static String normalizeCountryCode(String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "countryCode is required");
    }
    return CountryReferenceService.normalizeCountryCode(countryCode);
  }

  private static String normalizeSearch(String search) {
    if (search == null) {
      return null;
    }
    String trimmed = search.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String normalizeCountryFilter(String country) {
    if (country == null || country.isBlank()) {
      return null;
    }
    return country.trim().toUpperCase(Locale.ROOT);
  }
}
