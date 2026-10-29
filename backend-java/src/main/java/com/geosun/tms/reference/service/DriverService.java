package com.geosun.tms.reference.service;

import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.reference.domain.Driver;
import com.geosun.tms.reference.domain.DriverDocument;
import com.geosun.tms.reference.domain.DriverDocumentCompliance;
import com.geosun.tms.reference.domain.DriverDocumentStatus;
import com.geosun.tms.reference.domain.DriverDocumentType;
import com.geosun.tms.reference.domain.DriverListView;
import com.geosun.tms.reference.domain.RegistrationScanSide;
import com.geosun.tms.reference.dto.request.CreateDriverRequest;
import com.geosun.tms.reference.dto.request.UpdateDriverRequest;
import com.geosun.tms.reference.dto.response.DriverDto;
import com.geosun.tms.reference.dto.response.LinkableUserDto;
import com.geosun.tms.reference.repository.DriverDocumentRepository;
import com.geosun.tms.reference.repository.DriverRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

  private final DriverRepository driverRepository;
  private final DriverDocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final ActiveTripGuard activeTripGuard;
  private final CurrentEnterprise currentEnterprise;

  public DriverService(
      DriverRepository driverRepository,
      DriverDocumentRepository documentRepository,
      UserRepository userRepository,
      ActiveTripGuard activeTripGuard,
      CurrentEnterprise currentEnterprise) {
    this.driverRepository = driverRepository;
    this.documentRepository = documentRepository;
    this.userRepository = userRepository;
    this.activeTripGuard = activeTripGuard;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional(readOnly = true)
  public List<DriverDto> list(DriverListView view) {
    String enterpriseId = currentEnterprise.requireId();
    List<Driver> drivers =
        switch (view == null ? DriverListView.ACTIVE : view) {
          case ACTIVE ->
              driverRepository.findByEnterpriseIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(
                  enterpriseId);
          case DELETED ->
              driverRepository.findByEnterpriseIdAndDeletedTrueOrderByLastNameAscFirstNameAsc(
                  enterpriseId);
          case ALL ->
              driverRepository.findByEnterpriseIdOrderByLastNameAscFirstNameAsc(enterpriseId);
        };
    Map<String, DriverDocumentCompliance> complianceMap =
        computeComplianceMap(Objects.requireNonNull(drivers));
    return drivers.stream()
        .map(
            d -> {
              Driver driver = Objects.requireNonNull(d);
              return toDto(
                  driver,
                  complianceMap.getOrDefault(
                      Objects.requireNonNull(driver.getId()), DriverDocumentCompliance.PROBLEM));
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public DriverDto getById(@NonNull String id) {
    Driver driver = requireDriver(id);
    return toDto(driver, computeCompliance(driver));
  }

  @Transactional
  public DriverDto create(@NonNull CreateDriverRequest request) {
    Driver driver = new Driver();
    driver.setEnterpriseId(currentEnterprise.requireId());
    applyFields(
        driver,
        request.lastName(),
        request.firstName(),
        request.patronymic(),
        request.phone(),
        request.licenseNumber(),
        request.licenseCategories(),
        request.licenseExpiresOn(),
        request.comment());
    assertUniqueLicense(Objects.requireNonNull(driver.getLicenseNumber()), null);
    Driver saved = Objects.requireNonNull(driverRepository.save(driver));
    return toDto(saved, DriverDocumentCompliance.PROBLEM);
  }

  @Transactional
  public DriverDto update(@NonNull String id, @NonNull UpdateDriverRequest request) {
    Driver driver = requireActiveDriver(id);
    applyFields(
        driver,
        request.lastName(),
        request.firstName(),
        request.patronymic(),
        request.phone(),
        request.licenseNumber(),
        request.licenseCategories(),
        request.licenseExpiresOn(),
        request.comment());
    assertUniqueLicense(Objects.requireNonNull(driver.getLicenseNumber()), id);
    Driver saved = Objects.requireNonNull(driverRepository.save(driver));
    return toDto(saved, computeCompliance(saved));
  }

  @Transactional
  public void softDelete(@NonNull String id) {
    Driver driver = requireDriver(id);
    if (driver.isDeleted()) {
      return;
    }
    if (activeTripGuard.hasActiveTripForDriver(id)) {
      throw ApiException.conflict(
          "DRIVER_IN_ACTIVE_TRIP", "Driver is assigned to an active trip and cannot be deleted");
    }
    driver.setDeleted(true);
    driver.setDeletedAt(Instant.now());
    driverRepository.save(driver);
  }

  @Transactional
  public DriverDto restore(@NonNull String id) {
    Driver driver = requireDriver(id);
    if (!driver.isDeleted()) {
      return toDto(driver, computeCompliance(driver));
    }
    assertUniqueLicense(Objects.requireNonNull(driver.getLicenseNumber()), id);
    if (driver.getUserId() != null) {
      String linkedUserId = Objects.requireNonNull(driver.getUserId());
      if (driverRepository.existsByUserId(linkedUserId)) {
        Optional<Driver> other = driverRepository.findByUserId(linkedUserId);
        if (other.isPresent()
            && !Objects.equals(other.get().getId(), id)
            && !other.get().isDeleted()) {
          throw ApiException.conflict(
              "USER_ALREADY_LINKED", "User is already linked to another driver");
        }
      }
    }
    driver.setDeleted(false);
    driver.setDeletedAt(null);
    Driver saved = Objects.requireNonNull(driverRepository.save(driver));
    return toDto(saved, computeCompliance(saved));
  }

  @Transactional(readOnly = true)
  public LinkableUserDto findLinkableUser(@NonNull String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    User user =
        userRepository
            .findByEnterpriseIdAndEmailAndDeletedFalse(currentEnterprise.requireId(), normalized)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    if (!user.hasAvailableRole(Role.USER) && !user.hasAvailableRole(Role.DRIVER)) {
      throw ApiException.conflict(
          "USER_ROLE_NOT_LINKABLE", "Only USER or DRIVER accounts can be linked to a driver");
    }
    if (driverRepository.existsByUserId(Objects.requireNonNull(user.getId()))) {
      throw ApiException.conflict("USER_ALREADY_LINKED", "User is already linked to a driver");
    }
    return new LinkableUserDto(
        Objects.requireNonNull(user.getId()),
        Objects.requireNonNull(user.getEmail()),
        Objects.requireNonNull(user.getRole()).name());
  }

  @Transactional
  public DriverDto linkUser(@NonNull String driverId, @NonNull String userId) {
    Driver driver = requireActiveDriver(driverId);
    String enterpriseId = currentEnterprise.requireId();
    User user =
        userRepository
            .findById(userId)
            .filter(u -> !u.isDeleted() && enterpriseId.equals(u.getEnterpriseId()))
            .orElseThrow(() -> ApiException.notFound("User not found"));
    if (!user.hasAvailableRole(Role.USER) && !user.hasAvailableRole(Role.DRIVER)) {
      throw ApiException.conflict(
          "USER_ROLE_NOT_LINKABLE", "Only USER or DRIVER accounts can be linked to a driver");
    }
    Optional<Driver> existing = driverRepository.findByUserId(userId);
    if (existing.isPresent() && !Objects.equals(existing.get().getId(), driverId)) {
      throw ApiException.conflict(
          "USER_ALREADY_LINKED", "User is already linked to another driver");
    }
    user.ensureRoleAssigned(Role.DRIVER);
    if (user.getRole() == Role.USER) {
      user.setRole(Role.DRIVER);
    }
    userRepository.save(Objects.requireNonNull(user));
    driver.setUserId(userId);
    Driver saved = Objects.requireNonNull(driverRepository.save(driver));
    return toDto(saved, computeCompliance(saved));
  }

  @Transactional
  public DriverDto unlinkUser(@NonNull String driverId) {
    Driver driver = requireActiveDriver(driverId);
    driver.setUserId(null);
    Driver saved = Objects.requireNonNull(driverRepository.save(driver));
    return toDto(saved, computeCompliance(saved));
  }

  @NonNull
  public Driver requireDriver(@NonNull String id) {
    return Objects.requireNonNull(
        driverRepository
            .findByIdAndEnterpriseId(id, currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Driver not found")));
  }

  @NonNull
  public Driver requireActiveDriver(@NonNull String id) {
    Driver driver = requireDriver(id);
    if (driver.isDeleted()) {
      throw ApiException.conflict("DRIVER_DELETED", "Driver is deleted");
    }
    return driver;
  }

  @Nullable
  public Driver findByUserId(@NonNull String userId) {
    return driverRepository.findByUserId(userId).orElse(null);
  }

  private void applyFields(
      Driver driver,
      String lastName,
      String firstName,
      String patronymic,
      String phone,
      String licenseNumber,
      String licenseCategories,
      LocalDate licenseExpiresOn,
      String comment) {
    driver.setLastName(Objects.requireNonNull(lastName).trim());
    driver.setFirstName(Objects.requireNonNull(firstName).trim());
    driver.setPatronymic(patronymic != null && !patronymic.isBlank() ? patronymic.trim() : null);
    driver.setPhone(Objects.requireNonNull(phone).trim());
    driver.setLicenseNumber(Objects.requireNonNull(licenseNumber).trim());
    driver.setLicenseCategories(Objects.requireNonNull(licenseCategories).trim());
    driver.setLicenseExpiresOn(Objects.requireNonNull(licenseExpiresOn));
    driver.setComment(comment != null && !comment.isBlank() ? comment.trim() : null);
  }

  private void assertUniqueLicense(@NonNull String licenseNumber, @Nullable String excludeId) {
    String enterpriseId = currentEnterprise.requireId();
    boolean exists =
        excludeId == null
            ? driverRepository.existsByEnterpriseIdAndLicenseNumberIgnoreCaseAndDeletedFalse(
                enterpriseId, licenseNumber)
            : driverRepository
                .existsByEnterpriseIdAndLicenseNumberIgnoreCaseAndDeletedFalseAndIdNot(
                    enterpriseId, licenseNumber, excludeId);
    if (exists) {
      throw ApiException.conflict("LICENSE_ALREADY_EXISTS", "License number already exists");
    }
  }

  /**
   * Дата закінчення прав для бізнес-перевірок: мінімум серед актуальних сканів DRIVER_LICENSE,
   * інакше поле картки водія.
   */
  @NonNull
  public LocalDate resolveLicenseExpiresOn(@NonNull Driver driver) {
    LocalDate fromDocuments =
        minCurrentLicenseDocumentValidTo(Objects.requireNonNull(driver.getId()));
    if (fromDocuments != null) {
      return fromDocuments;
    }
    return Objects.requireNonNull(driver.getLicenseExpiresOn());
  }

  @Transactional
  public void syncLicenseExpiresOnFromDocuments(@NonNull String driverId) {
    LocalDate fromDocuments = minCurrentLicenseDocumentValidTo(driverId);
    if (fromDocuments == null) {
      return;
    }
    Driver driver = requireActiveDriver(driverId);
    if (!fromDocuments.equals(driver.getLicenseExpiresOn())) {
      driver.setLicenseExpiresOn(fromDocuments);
      driverRepository.save(driver);
    }
  }

  @Nullable
  private LocalDate minCurrentLicenseDocumentValidTo(@NonNull String driverId) {
    LocalDate min = null;
    for (RegistrationScanSide side : RegistrationScanSide.values()) {
      Optional<DriverDocument> doc =
          documentRepository.findFirstByDriver_IdAndDocumentTypeAndSideOrderByCreatedAtDesc(
              driverId, DriverDocumentType.DRIVER_LICENSE, side);
      if (doc.isPresent()) {
        LocalDate validTo = Objects.requireNonNull(doc.get().getValidTo());
        min = min == null || validTo.isBefore(min) ? validTo : min;
      }
    }
    return min;
  }

  @NonNull
  private Map<String, DriverDocumentCompliance> computeComplianceMap(
      @NonNull List<Driver> drivers) {
    Map<String, DriverDocumentCompliance> map = new HashMap<>();
    LocalDate today = LocalDate.now();
    for (Driver driver : drivers) {
      Driver d = Objects.requireNonNull(driver);
      map.put(Objects.requireNonNull(d.getId()), computeCompliance(d, today));
    }
    return map;
  }

  private DriverDocumentCompliance computeCompliance(Driver driver) {
    return computeCompliance(driver, LocalDate.now());
  }

  private DriverDocumentCompliance computeCompliance(Driver driver, LocalDate today) {
    List<DriverDocument> docs =
        documentRepository.findByDriver_IdOrderByDocumentTypeAscSideAscCreatedAtDesc(
            Objects.requireNonNull(driver.getId()));
    boolean anyMissingOrExpired = false;
    boolean anyExpiringSoon = false;
    for (DriverDocumentRules.DocumentSlot slot : DriverDocumentRules.requiredSlots()) {
      DriverDocument current =
          docs.stream()
              .filter(
                  d ->
                      Objects.requireNonNull(d).getDocumentType() == slot.type()
                          && d.getSide() == slot.side())
              .findFirst()
              .orElse(null);
      DriverDocumentStatus status =
          DriverDocumentRules.statusOf(
              current == null ? null : current.getValidTo(), Objects.requireNonNull(today));
      if (status == DriverDocumentStatus.MISSING || status == DriverDocumentStatus.EXPIRED) {
        anyMissingOrExpired = true;
      } else if (status == DriverDocumentStatus.EXPIRING_SOON) {
        anyExpiringSoon = true;
      }
    }
    if (anyMissingOrExpired) {
      return DriverDocumentCompliance.PROBLEM;
    }
    if (anyExpiringSoon) {
      return DriverDocumentCompliance.ATTENTION;
    }
    return DriverDocumentCompliance.OK;
  }

  private DriverDto toDto(Driver driver, DriverDocumentCompliance compliance) {
    String email = null;
    if (driver.getUserId() != null) {
      email =
          userRepository
              .findById(Objects.requireNonNull(driver.getUserId()))
              .map(u -> u.getEmail())
              .orElse(null);
    }
    return new DriverDto(
        Objects.requireNonNull(driver.getId()),
        Objects.requireNonNull(driver.getLastName()),
        Objects.requireNonNull(driver.getFirstName()),
        driver.getPatronymic(),
        Objects.requireNonNull(driver.getPhone()),
        Objects.requireNonNull(driver.getLicenseNumber()),
        Objects.requireNonNull(driver.getLicenseCategories()),
        Objects.requireNonNull(driver.getLicenseExpiresOn()),
        driver.getUserId(),
        email,
        driver.getComment(),
        compliance,
        driver.isDeleted(),
        driver.getDeletedAt(),
        driver.getCreatedAt(),
        driver.getUpdatedAt());
  }
}
