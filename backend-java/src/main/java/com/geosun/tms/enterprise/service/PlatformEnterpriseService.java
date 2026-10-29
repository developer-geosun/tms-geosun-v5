package com.geosun.tms.enterprise.service;

import com.geosun.tms.auth.domain.EmailNormalizer;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.domain.EnterpriseCodeRules;
import com.geosun.tms.enterprise.dto.CreateEnterpriseRequest;
import com.geosun.tms.enterprise.dto.EnterpriseDto;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.reference.service.DocumentTypeReferenceService;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Керування підприємствами оператором платформи: список, створення з першим ADMIN, призупинення.
 *
 * <p>Створення підприємства копіює каталог типів документів першого підприємства (нові id);
 * сценарії та тарифи фрахту порожні.
 */
@Service
public class PlatformEnterpriseService {

  private final EnterpriseRepository enterpriseRepository;
  private final PlatformOperatorRepository platformOperatorRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final DocumentTypeReferenceService documentTypeReferenceService;

  public PlatformEnterpriseService(
      EnterpriseRepository enterpriseRepository,
      PlatformOperatorRepository platformOperatorRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      DocumentTypeReferenceService documentTypeReferenceService) {
    this.enterpriseRepository = enterpriseRepository;
    this.platformOperatorRepository = platformOperatorRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.documentTypeReferenceService = documentTypeReferenceService;
  }

  @Transactional(readOnly = true)
  public List<EnterpriseDto> list() {
    return enterpriseRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(e -> toDto(Objects.requireNonNull(e)))
        .toList();
  }

  @Transactional
  public EnterpriseDto create(@NonNull CreateEnterpriseRequest request) {
    String code = EnterpriseCodeRules.normalize(request.code());
    if (code == null) {
      throw ApiException.badRequest(
          "INVALID_ENTERPRISE_CODE",
          "Code must be 2-32 latin letters, digits or hyphen and must not be 'platform'");
    }
    String name = request.name() == null ? "" : request.name().trim();
    if (name.isEmpty() || name.length() > 200) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Name is required (max 200 characters)");
    }
    if (enterpriseRepository.existsByCode(code)) {
      throw ApiException.conflict("ENTERPRISE_CODE_EXISTS", "Enterprise code is already in use");
    }
    String adminEmail = EmailNormalizer.normalize(request.adminEmail());
    if (platformOperatorRepository.existsByEmail(adminEmail)) {
      throw ApiException.conflict("Email is already registered");
    }

    // Джерело копії каталогу — перше підприємство (визначаємо до створення нового)
    String sourceEnterpriseId =
        enterpriseRepository
            .findFirstByOrderByCreatedAtAsc()
            .map(enterpriseRow -> enterpriseRow.getId())
            .orElse(null);

    Enterprise enterprise = new Enterprise();
    enterprise.setCode(code);
    enterprise.setName(name);
    enterprise.setActive(true);
    Enterprise saved = Objects.requireNonNull(enterpriseRepository.saveAndFlush(enterprise));
    String enterpriseId = Objects.requireNonNull(saved.getId());

    // Нове підприємство отримує власну копію видів документів; сценарії й тарифи порожні
    documentTypeReferenceService.copyCatalogToEnterprise(enterpriseId, sourceEnterpriseId);

    User admin = new User();
    admin.setEnterpriseId(enterpriseId);
    admin.setEmail(adminEmail);
    admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
    admin.setRole(Role.ADMIN);
    admin.replaceAvailableRoles(Objects.requireNonNull(EnumSet.of(Role.ADMIN)));
    admin.setActive(true);
    admin.setEmailVerified(true);
    admin.setEmailVerifiedAt(Instant.now());
    userRepository.save(admin);

    return toDto(saved);
  }

  @Transactional
  public EnterpriseDto suspend(@NonNull String rawId) {
    return setActive(rawId, false);
  }

  @Transactional
  public EnterpriseDto activate(@NonNull String rawId) {
    return setActive(rawId, true);
  }

  private EnterpriseDto setActive(@NonNull String rawId, boolean active) {
    try {
      UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid enterprise id");
    }
    Enterprise enterprise =
        enterpriseRepository
            .findById(rawId)
            .orElseThrow(() -> ApiException.notFound("Enterprise not found"));
    if (enterprise.isActive() != active) {
      enterprise.setActive(active);
      enterprise =
          Objects.requireNonNull(
              enterpriseRepository.saveAndFlush(Objects.requireNonNull(enterprise)));
    }
    return toDto(enterprise);
  }

  private static EnterpriseDto toDto(Enterprise e) {
    return new EnterpriseDto(
        e.getId(), e.getCode(), e.getName(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
  }
}
