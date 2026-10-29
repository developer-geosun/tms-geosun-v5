package com.geosun.tms.reference.service;

import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.reference.domain.RegistrationScanSide;
import com.geosun.tms.reference.domain.Vehicle;
import com.geosun.tms.reference.domain.VehicleDocument;
import com.geosun.tms.reference.domain.VehicleDocumentCompliance;
import com.geosun.tms.reference.domain.VehicleDocumentStatus;
import com.geosun.tms.reference.domain.VehicleDocumentType;
import com.geosun.tms.reference.domain.VehicleListView;
import com.geosun.tms.reference.domain.VehicleRegistrationScan;
import com.geosun.tms.reference.domain.VehicleType;
import com.geosun.tms.reference.dto.request.CreateVehicleRequest;
import com.geosun.tms.reference.dto.request.UpdateVehicleRequest;
import com.geosun.tms.reference.dto.response.VehicleDto;
import com.geosun.tms.reference.repository.VehicleDocumentRepository;
import com.geosun.tms.reference.repository.VehicleRegistrationScanRepository;
import com.geosun.tms.reference.repository.VehicleRepository;
import com.geosun.tms.storage.dto.StoredFileDto;
import com.geosun.tms.storage.service.StoredFileService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VehicleService {

  private static final Pattern VIN_PATTERN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

  /** Стандартний UA-номер: 2 літери + 4 цифри + 2 літери (латиниця A–Z після нормалізації). */
  private static final Pattern PLATE_PATTERN = Pattern.compile("^[A-Z]{2}\\d{4}[A-Z]{2}$");

  private final VehicleRepository vehicleRepository;
  private final VehicleRegistrationScanRepository scanRepository;
  private final VehicleDocumentRepository documentRepository;
  private final StoredFileService storedFileService;
  private final CurrentEnterprise currentEnterprise;

  public VehicleService(
      VehicleRepository vehicleRepository,
      VehicleRegistrationScanRepository scanRepository,
      VehicleDocumentRepository documentRepository,
      StoredFileService storedFileService,
      CurrentEnterprise currentEnterprise) {
    this.vehicleRepository = vehicleRepository;
    this.scanRepository = scanRepository;
    this.documentRepository = documentRepository;
    this.storedFileService = storedFileService;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional(readOnly = true)
  public List<VehicleDto> list(VehicleListView view) {
    String enterpriseId = currentEnterprise.requireId();
    List<Vehicle> vehicles =
        switch (view == null ? VehicleListView.ACTIVE : view) {
          case ACTIVE ->
              vehicleRepository.findByEnterpriseIdAndDeletedFalseOrderByPlateNumberAsc(
                  enterpriseId);
          case DELETED ->
              vehicleRepository.findByEnterpriseIdAndDeletedTrueOrderByPlateNumberAsc(enterpriseId);
          case ALL -> vehicleRepository.findByEnterpriseIdOrderByPlateNumberAsc(enterpriseId);
        };
    Map<String, VehicleDocumentCompliance> complianceMap =
        computeComplianceMap(Objects.requireNonNull(vehicles));
    return vehicles.stream()
        .map(
            v -> toDto(v, complianceMap.getOrDefault(v.getId(), VehicleDocumentCompliance.PROBLEM)))
        .toList();
  }

  @Transactional(readOnly = true)
  public VehicleDto getById(@NonNull String id) {
    Vehicle vehicle = requireVehicle(id);
    return toDto(vehicle, computeCompliance(vehicle));
  }

  @Transactional
  public VehicleDto create(@NonNull CreateVehicleRequest request) {
    Vehicle vehicle = new Vehicle();
    vehicle.setEnterpriseId(currentEnterprise.requireId());
    applyFields(
        vehicle,
        request.plateNumber(),
        request.vin(),
        request.make(),
        request.model(),
        request.manufactureYear(),
        request.owner(),
        request.registrationSeries(),
        request.registrationNumber(),
        request.vehicleType(),
        request.hasRefrigerator());
    assertUnique(vehicle, null);
    Vehicle saved = vehicleRepository.save(vehicle);
    return toDto(saved, computeCompliance(saved));
  }

  @Transactional
  public VehicleDto update(@NonNull String id, @NonNull UpdateVehicleRequest request) {
    Vehicle vehicle = requireVehicle(id);
    if (vehicle.isDeleted()) {
      throw ApiException.conflict("VEHICLE_DELETED", "Cannot update a deleted vehicle");
    }
    applyFields(
        vehicle,
        request.plateNumber(),
        request.vin(),
        request.make(),
        request.model(),
        request.manufactureYear(),
        request.owner(),
        request.registrationSeries(),
        request.registrationNumber(),
        request.vehicleType(),
        request.hasRefrigerator());
    assertUnique(vehicle, id);
    Vehicle saved = vehicleRepository.save(vehicle);
    return toDto(saved, computeCompliance(saved));
  }

  @Transactional
  public void softDelete(@NonNull String id) {
    Vehicle vehicle = requireVehicle(id);
    if (vehicle.isDeleted()) {
      return;
    }
    vehicle.setDeleted(true);
    vehicle.setDeletedAt(Instant.now());
    vehicleRepository.save(vehicle);
  }

  @Transactional
  public VehicleDto restore(@NonNull String id) {
    Vehicle vehicle = requireVehicle(id);
    if (!vehicle.isDeleted()) {
      return toDto(vehicle, computeCompliance(vehicle));
    }
    assertUnique(vehicle, id);
    vehicle.setDeleted(false);
    vehicle.setDeletedAt(null);
    Vehicle saved = vehicleRepository.save(vehicle);
    return toDto(saved, computeCompliance(saved));
  }

  @NonNull
  public Vehicle requireVehicle(@NonNull String id) {
    return Objects.requireNonNull(
        vehicleRepository
            .findByIdAndEnterpriseId(id, currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Vehicle not found")));
  }

  @NonNull
  public Vehicle requireActiveVehicle(@NonNull String id) {
    Vehicle vehicle = requireVehicle(id);
    if (vehicle.isDeleted()) {
      throw ApiException.conflict("VEHICLE_DELETED", "Vehicle is deleted");
    }
    return vehicle;
  }

  private void applyFields(
      Vehicle vehicle,
      String plateNumber,
      String vin,
      String make,
      String model,
      Short manufactureYear,
      String owner,
      String registrationSeries,
      String registrationNumber,
      VehicleType vehicleType,
      Boolean hasRefrigerator) {
    vehicle.setPlateNumber(normalizePlateNumber(plateNumber));
    vehicle.setVin(normalizeVin(vin));
    vehicle.setMake(normalizeUpperText(make, "make"));
    vehicle.setModel(normalizeUpperText(model, "model"));
    vehicle.setManufactureYear(validateYear(manufactureYear));
    vehicle.setOwner(requireTrimmed(owner, "owner"));
    vehicle.setRegistrationSeries(normalizeUpperText(registrationSeries, "registrationSeries"));
    vehicle.setRegistrationNumber(normalizeUpperText(registrationNumber, "registrationNumber"));
    if (vehicleType == null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "vehicleType is required");
    }
    vehicle.setVehicleType(vehicleType);
    // Для тягача прапор рефрижератора завжди false
    boolean fridge =
        Boolean.TRUE.equals(hasRefrigerator) && vehicleType == VehicleType.SEMI_TRAILER;
    vehicle.setHasRefrigerator(fridge);
  }

  private void assertUnique(Vehicle vehicle, String excludeId) {
    String enterpriseId = currentEnterprise.requireId();
    String plate = vehicle.getPlateNumber();
    String vin = vehicle.getVin();
    String series = vehicle.getRegistrationSeries();
    String number = vehicle.getRegistrationNumber();

    boolean plateConflict =
        excludeId == null
            ? vehicleRepository.existsByEnterpriseIdAndPlateNumberIgnoreCaseAndDeletedFalse(
                enterpriseId, plate)
            : vehicleRepository.existsByEnterpriseIdAndPlateNumberIgnoreCaseAndDeletedFalseAndIdNot(
                enterpriseId, plate, excludeId);
    if (plateConflict) {
      throw ApiException.conflict("PLATE_ALREADY_EXISTS", "Plate number already exists");
    }

    boolean vinConflict =
        excludeId == null
            ? vehicleRepository.existsByEnterpriseIdAndVinIgnoreCaseAndDeletedFalse(
                enterpriseId, vin)
            : vehicleRepository.existsByEnterpriseIdAndVinIgnoreCaseAndDeletedFalseAndIdNot(
                enterpriseId, vin, excludeId);
    if (vinConflict) {
      throw ApiException.conflict("VIN_ALREADY_EXISTS", "VIN already exists");
    }

    boolean regConflict =
        excludeId == null
            ? vehicleRepository
                .existsByEnterpriseIdAndRegistrationSeriesIgnoreCaseAndRegistrationNumberIgnoreCaseAndDeletedFalse(
                    enterpriseId, series, number)
            : vehicleRepository
                .existsByEnterpriseIdAndRegistrationSeriesIgnoreCaseAndRegistrationNumberIgnoreCaseAndDeletedFalseAndIdNot(
                    enterpriseId, series, number, excludeId);
    if (regConflict) {
      throw ApiException.conflict(
          "REGISTRATION_ALREADY_EXISTS", "Registration series/number already exists");
    }
  }

  private VehicleDto toDto(Vehicle vehicle, VehicleDocumentCompliance compliance) {
    Map<RegistrationScanSide, StoredFileDto> scans =
        loadScanMap(Objects.requireNonNull(vehicle.getId()));
    return new VehicleDto(
        vehicle.getId(),
        vehicle.getPlateNumber(),
        vehicle.getVin(),
        vehicle.getMake(),
        vehicle.getModel(),
        vehicle.getManufactureYear(),
        vehicle.getOwner(),
        vehicle.getRegistrationSeries(),
        vehicle.getRegistrationNumber(),
        vehicle.getVehicleType(),
        vehicle.isHasRefrigerator(),
        compliance,
        vehicle.isDeleted(),
        vehicle.getDeletedAt(),
        vehicle.getCreatedAt(),
        vehicle.getUpdatedAt(),
        scans.get(RegistrationScanSide.FRONT),
        scans.get(RegistrationScanSide.BACK));
  }

  @NonNull
  private VehicleDocumentCompliance computeCompliance(@NonNull Vehicle vehicle) {
    List<VehicleDocument> docs =
        documentRepository.findByVehicle_IdOrderByDocumentTypeAscCreatedAtDesc(
            Objects.requireNonNull(vehicle.getId()));
    return complianceOf(vehicle, docs, LocalDate.now());
  }

  @NonNull
  private Map<String, VehicleDocumentCompliance> computeComplianceMap(
      @NonNull List<Vehicle> vehicles) {
    Map<String, VehicleDocumentCompliance> result = new HashMap<>();
    if (vehicles.isEmpty()) {
      return result;
    }
    List<String> ids =
        vehicles.stream().map(v -> Objects.requireNonNull(v.getId())).distinct().toList();
    List<VehicleDocument> allDocs = documentRepository.findByVehicle_IdIn(ids);
    Map<String, List<VehicleDocument>> byVehicle = new HashMap<>();
    for (VehicleDocument doc : allDocs) {
      String vid = Objects.requireNonNull(doc.getVehicle().getId());
      byVehicle.computeIfAbsent(vid, k -> new ArrayList<>()).add(doc);
    }
    LocalDate today = LocalDate.now();
    for (Vehicle vehicle : vehicles) {
      String id = Objects.requireNonNull(vehicle.getId());
      result.put(id, complianceOf(vehicle, byVehicle.getOrDefault(id, List.of()), today));
    }
    return result;
  }

  @NonNull
  private static VehicleDocumentCompliance complianceOf(
      Vehicle vehicle, List<VehicleDocument> allDocs, LocalDate today) {
    Set<VehicleDocumentType> required =
        VehicleDocumentRules.requiredTypes(
            Objects.requireNonNull(vehicle.getVehicleType()), vehicle.isHasRefrigerator());
    Map<VehicleDocumentType, VehicleDocument> currentByType =
        new EnumMap<>(VehicleDocumentType.class);
    allDocs.stream()
        .sorted(VehicleDocumentRules.newestFirst())
        .forEach(doc -> currentByType.putIfAbsent(doc.getDocumentType(), doc));

    boolean hasExpiredOrMissing = false;
    boolean hasExpiringSoon = false;
    for (VehicleDocumentType type : required) {
      VehicleDocument current = currentByType.get(type);
      VehicleDocumentStatus status =
          current == null
              ? VehicleDocumentStatus.MISSING
              : VehicleDocumentRules.statusOf(current.getValidTo(), Objects.requireNonNull(today));
      if (status == VehicleDocumentStatus.MISSING || status == VehicleDocumentStatus.EXPIRED) {
        hasExpiredOrMissing = true;
      } else if (status == VehicleDocumentStatus.EXPIRING_SOON) {
        hasExpiringSoon = true;
      }
    }
    if (hasExpiredOrMissing) {
      return VehicleDocumentCompliance.PROBLEM;
    }
    if (hasExpiringSoon) {
      return VehicleDocumentCompliance.ATTENTION;
    }
    return VehicleDocumentCompliance.OK;
  }

  private Map<RegistrationScanSide, StoredFileDto> loadScanMap(String vehicleId) {
    Map<RegistrationScanSide, StoredFileDto> map = new EnumMap<>(RegistrationScanSide.class);
    for (VehicleRegistrationScan scan :
        scanRepository.findByVehicle_Id(Objects.requireNonNull(vehicleId))) {
      map.put(
          scan.getSide(),
          storedFileService.getDto(Objects.requireNonNull(scan.getStoredFile().getId())));
    }
    return map;
  }

  private static String requireTrimmed(String value, String field) {
    if (!StringUtils.hasText(value)) {
      throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
    }
    return value.trim();
  }

  /** Trim + UPPERCASE (для марки/моделі). */
  private static String normalizeUpperText(String value, String field) {
    return requireTrimmed(value, field).toUpperCase(Locale.ROOT);
  }

  /**
   * Нормалізація держномера UA: UPPERCASE, без пробілів/зайвих символів, кирилиця → латиниця,
   * формат LL####LL.
   */
  private static String normalizePlateNumber(String plateNumber) {
    if (!StringUtils.hasText(plateNumber)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "plateNumber is required");
    }
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < plateNumber.length(); i++) {
      char mapped = mapUaPlateChar(Character.toUpperCase(plateNumber.charAt(i)));
      if (mapped != 0) {
        sb.append(mapped);
        if (sb.length() == 8) {
          break;
        }
      }
    }
    String normalized = sb.toString();
    if (!PLATE_PATTERN.matcher(normalized).matches()) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR", "plateNumber must be UA format LL####LL with Latin letters A-Z");
    }
    return normalized;
  }

  /** Дозволений символ → латиниця/цифра; інакше 0. */
  private static char mapUaPlateChar(char ch) {
    return switch (ch) {
      case 'A', 'А' -> 'A';
      case 'B', 'В' -> 'B';
      case 'C', 'С' -> 'C';
      case 'D', 'Д' -> 'D';
      case 'E', 'Е' -> 'E';
      case 'F', 'Ф' -> 'F';
      case 'G' -> 'G';
      case 'H', 'Н' -> 'H';
      case 'I', 'І' -> 'I';
      case 'J' -> 'J';
      case 'K', 'К' -> 'K';
      case 'L', 'Л' -> 'L';
      case 'M', 'М' -> 'M';
      case 'N' -> 'N';
      case 'O', 'О' -> 'O';
      case 'P', 'Р' -> 'P';
      case 'Q' -> 'Q';
      case 'R' -> 'R';
      case 'S' -> 'S';
      case 'T', 'Т' -> 'T';
      case 'U', 'У' -> 'U';
      case 'V' -> 'V';
      case 'W' -> 'W';
      case 'X', 'Х' -> 'X';
      case 'Y' -> 'Y';
      case 'Z' -> 'Z';
      case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> ch;
      default -> 0;
    };
  }

  private static String normalizeVin(String vin) {
    if (!StringUtils.hasText(vin)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "vin is required");
    }
    StringBuilder sb = new StringBuilder(17);
    for (int i = 0; i < vin.length(); i++) {
      char ch = Character.toUpperCase(vin.charAt(i));
      if (isAllowedVinChar(ch)) {
        sb.append(ch);
        if (sb.length() == 17) {
          break;
        }
      }
    }
    String normalized = sb.toString();
    if (!VIN_PATTERN.matcher(normalized).matches()) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR", "VIN must be 17 characters without I, O, Q");
    }
    return normalized;
  }

  private static boolean isAllowedVinChar(char ch) {
    return (ch >= 'A' && ch <= 'H')
        || (ch >= 'J' && ch <= 'N')
        || ch == 'P'
        || (ch >= 'R' && ch <= 'Z')
        || (ch >= '0' && ch <= '9');
  }

  private static short validateYear(Short year) {
    if (year == null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "manufactureYear is required");
    }
    int max = Year.now().getValue() + 1;
    if (year < 1950 || year > max) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR", "manufactureYear must be between 1950 and " + max);
    }
    return year;
  }
}
