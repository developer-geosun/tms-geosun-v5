package com.geosun.tms.trips.service;

import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.reference.domain.Driver;
import com.geosun.tms.reference.domain.Vehicle;
import com.geosun.tms.reference.domain.VehicleCombination;
import com.geosun.tms.reference.domain.VehicleType;
import com.geosun.tms.reference.repository.VehicleRepository;
import com.geosun.tms.reference.service.DriverService;
import com.geosun.tms.reference.service.VehicleCombinationService;
import com.geosun.tms.routes.repository.RouteRequestRepository;
import com.geosun.tms.trips.domain.Trip;
import com.geosun.tms.trips.domain.TripExpenseReport;
import com.geosun.tms.trips.domain.TripExpenseReportStatus;
import com.geosun.tms.trips.domain.TripListView;
import com.geosun.tms.trips.domain.TripNumberSeq;
import com.geosun.tms.trips.domain.TripStatus;
import com.geosun.tms.trips.dto.request.CreateTripRequest;
import com.geosun.tms.trips.dto.request.UpdateTripRequest;
import com.geosun.tms.trips.dto.response.PageResponse;
import com.geosun.tms.trips.dto.response.TripDto;
import com.geosun.tms.trips.repository.TripExpenseReportRepository;
import com.geosun.tms.trips.repository.TripNumberSeqRepository;
import com.geosun.tms.trips.repository.TripRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TripService {

  private static final Set<TripStatus> OVERLAP_STATUSES =
      EnumSet.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS);

  private final TripRepository tripRepository;
  private final TripNumberSeqRepository tripNumberSeqRepository;
  private final TripExpenseReportRepository expenseReportRepository;
  private final DriverService driverService;
  private final VehicleCombinationService combinationService;
  private final VehicleRepository vehicleRepository;
  private final RouteRequestRepository routeRequestRepository;
  private final CurrentEnterprise currentEnterprise;

  public TripService(
      TripRepository tripRepository,
      TripNumberSeqRepository tripNumberSeqRepository,
      TripExpenseReportRepository expenseReportRepository,
      DriverService driverService,
      VehicleCombinationService combinationService,
      VehicleRepository vehicleRepository,
      RouteRequestRepository routeRequestRepository,
      CurrentEnterprise currentEnterprise) {
    this.currentEnterprise = currentEnterprise;
    this.tripRepository = tripRepository;
    this.tripNumberSeqRepository = tripNumberSeqRepository;
    this.expenseReportRepository = expenseReportRepository;
    this.driverService = driverService;
    this.combinationService = combinationService;
    this.vehicleRepository = vehicleRepository;
    this.routeRequestRepository = routeRequestRepository;
  }

  @Transactional(readOnly = true)
  public PageResponse<TripDto> listAdmin(
      TripListView view,
      @Nullable TripStatus status,
      @Nullable String driverId,
      @Nullable Instant from,
      @Nullable Instant to,
      int page,
      int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable =
        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Trip> result;
    TripListView effective = view == null ? TripListView.ACTIVE : view;
    String enterpriseId = currentEnterprise.requireId();
    if (effective == TripListView.ACTIVE
        && (status != null || driverId != null || from != null || to != null)) {
      result =
          tripRepository.findActiveFiltered(enterpriseId, status, driverId, from, to, pageable);
    } else {
      result =
          switch (effective) {
            case ACTIVE -> tripRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId, pageable);
            case DELETED -> tripRepository.findByEnterpriseIdAndDeletedTrue(enterpriseId, pageable);
            case ALL -> tripRepository.findByEnterpriseId(enterpriseId, pageable);
          };
    }
    List<TripDto> content =
        result.getContent().stream().map(t -> toDto(Objects.requireNonNull(t))).toList();
    return new PageResponse<>(
        content,
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public PageResponse<TripDto> listMy(@NonNull String userId, int page, int size) {
    Driver driver = driverService.findByUserId(userId);
    if (driver == null) {
      return new PageResponse<>(List.of(), 0, 0, page, size);
    }
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable =
        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "plannedStartAt"));
    Page<Trip> result =
        tripRepository.findByEnterpriseIdAndDriverIdAndDeletedFalse(
            currentEnterprise.requireId(), Objects.requireNonNull(driver.getId()), pageable);
    List<TripDto> content =
        result.getContent().stream().map(t -> toDto(Objects.requireNonNull(t))).toList();
    return new PageResponse<>(
        content,
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public TripDto getAdmin(@NonNull String id) {
    return toDto(requireTrip(id));
  }

  @Transactional(readOnly = true)
  public TripDto getMy(@NonNull String id, @NonNull String userId) {
    return toDto(requireOwnedTrip(id, userId));
  }

  @Transactional
  public TripDto create(@NonNull CreateTripRequest request) {
    Trip trip = new Trip();
    String enterpriseId = currentEnterprise.requireId();
    trip.setEnterpriseId(enterpriseId);
    trip.setTripNumber(nextTripNumber(enterpriseId));
    trip.setStatus(TripStatus.DRAFT);
    applyAssignmentAndMeta(
        trip,
        request.routeRequestId(),
        request.title(),
        request.comment(),
        request.originText(),
        request.destinationText(),
        request.plannedStartAt(),
        request.plannedEndAt(),
        request.driverId(),
        request.combinationId(),
        request.tractorId(),
        request.trailerId(),
        true);
    Trip saved = tripRepository.save(trip);
    TripExpenseReport report = new TripExpenseReport();
    report.setTripId(Objects.requireNonNull(saved.getId()));
    report.setStatus(TripExpenseReportStatus.DRAFT);
    expenseReportRepository.save(report);
    return toDto(Objects.requireNonNull(saved));
  }

  @Transactional
  public TripDto update(@NonNull String id, @NonNull UpdateTripRequest request) {
    Trip trip = requireActiveTrip(id);
    if (trip.getStatus() == TripStatus.IN_PROGRESS
        || trip.getStatus() == TripStatus.COMPLETED
        || trip.getStatus() == TripStatus.CANCELLED) {
      throw ApiException.conflict("TRIP_LOCKED", "Trip cannot be modified in current status");
    }
    applyAssignmentAndMeta(
        trip,
        request.routeRequestId(),
        request.title(),
        request.comment(),
        request.originText(),
        request.destinationText(),
        request.plannedStartAt(),
        request.plannedEndAt(),
        request.driverId(),
        request.combinationId(),
        request.tractorId(),
        request.trailerId(),
        true);
    return toDto(Objects.requireNonNull(tripRepository.save(trip)));
  }

  @Transactional
  public TripDto updateStatus(@NonNull String id, @NonNull TripStatus target) {
    Trip trip = requireActiveTrip(id);
    TripStatus current = trip.getStatus();
    if (current == target) {
      return toDto(trip);
    }
    assertTransition(current, target);
    if (target == TripStatus.PLANNED) {
      assertReadyForPlanned(trip);
      assertNoOverlap(trip);
      assertLicenseValid(trip);
    }
    if (target == TripStatus.IN_PROGRESS) {
      trip.setActualStartAt(Instant.now());
    }
    if (target == TripStatus.COMPLETED) {
      trip.setActualEndAt(Instant.now());
    }
    trip.setStatus(target);
    return toDto(Objects.requireNonNull(tripRepository.save(trip)));
  }

  @Transactional
  public void softDelete(@NonNull String id) {
    Trip trip = requireTrip(id);
    if (trip.isDeleted()) {
      return;
    }
    if (trip.getStatus() == TripStatus.IN_PROGRESS) {
      throw ApiException.conflict("TRIP_LOCKED", "Cannot delete trip in progress");
    }
    trip.setDeleted(true);
    trip.setDeletedAt(Instant.now());
    tripRepository.save(trip);
  }

  @Transactional
  public TripDto restore(@NonNull String id) {
    Trip trip = requireTrip(id);
    if (!trip.isDeleted()) {
      return toDto(trip);
    }
    if (trip.getRouteRequestId() != null
        && tripRepository.existsByRouteRequestIdAndDeletedFalseAndIdNot(
            Objects.requireNonNull(trip.getRouteRequestId()), id)) {
      throw ApiException.conflict(
          "REQUEST_ALREADY_HAS_TRIP", "Route request already has another active trip");
    }
    trip.setDeleted(false);
    trip.setDeletedAt(null);
    return toDto(Objects.requireNonNull(tripRepository.save(trip)));
  }

  @NonNull
  public Trip requireTrip(@NonNull String id) {
    return Objects.requireNonNull(
        tripRepository
            .findByIdAndEnterpriseId(id, currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Trip not found")));
  }

  @NonNull
  public Trip requireActiveTrip(@NonNull String id) {
    Trip trip = requireTrip(id);
    if (trip.isDeleted()) {
      throw ApiException.conflict("TRIP_DELETED", "Trip is deleted");
    }
    return trip;
  }

  @NonNull
  public Trip requireOwnedTrip(@NonNull String id, @NonNull String userId) {
    Trip trip = requireActiveTrip(id);
    Driver driver = driverService.findByUserId(userId);
    if (driver == null || !Objects.equals(driver.getId(), trip.getDriverId())) {
      throw ApiException.forbidden("FORBIDDEN", "Trip is not assigned to the current driver");
    }
    return trip;
  }

  private void applyAssignmentAndMeta(
      Trip trip,
      Long routeRequestId,
      String title,
      String comment,
      String originText,
      String destinationText,
      Instant plannedStartAt,
      Instant plannedEndAt,
      String driverId,
      String combinationId,
      String tractorId,
      String trailerId,
      boolean allowClear) {
    if (routeRequestId != null) {
      if (!routeRequestRepository.existsByIdAndEnterpriseId(
          routeRequestId, currentEnterprise.requireId())) {
        throw ApiException.notFound("Route request not found");
      }
      boolean conflict =
          trip.getId() == null
              ? tripRepository.existsByRouteRequestIdAndDeletedFalse(routeRequestId)
              : tripRepository.existsByRouteRequestIdAndDeletedFalseAndIdNot(
                  routeRequestId, Objects.requireNonNull(trip.getId()));
      if (conflict) {
        throw ApiException.conflict("REQUEST_ALREADY_HAS_TRIP", "Route request already has a trip");
      }
      trip.setRouteRequestId(routeRequestId);
    } else if (allowClear) {
      trip.setRouteRequestId(null);
    }

    trip.setTitle(trimOrNull(title));
    trip.setComment(trimOrNull(comment));
    trip.setOriginText(trimOrNull(originText));
    trip.setDestinationText(trimOrNull(destinationText));

    if (plannedStartAt != null && plannedEndAt != null && plannedEndAt.isBefore(plannedStartAt)) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR", "plannedEndAt must be on or after plannedStartAt");
    }
    trip.setPlannedStartAt(plannedStartAt);
    trip.setPlannedEndAt(plannedEndAt);

    resolveAssignment(trip, driverId, combinationId, tractorId, trailerId);
  }

  private void resolveAssignment(
      Trip trip, String driverId, String combinationId, String tractorId, String trailerId) {
    if (StringUtils.hasText(driverId)) {
      Driver driver = driverService.requireActiveDriver(Objects.requireNonNull(driverId));
      trip.setDriverId(Objects.requireNonNull(driver.getId()));
      trip.setDriverName(formatDriverName(driver));
    } else {
      trip.setDriverId(null);
      trip.setDriverName(null);
    }

    String resolvedTractorId = tractorId;
    String resolvedTrailerId = trailerId;
    String resolvedCombinationId = null;

    if (StringUtils.hasText(combinationId)) {
      VehicleCombination combination =
          combinationService.requireActiveCombination(Objects.requireNonNull(combinationId));
      resolvedTractorId = Objects.requireNonNull(combination.getTractor().getId());
      resolvedTrailerId = Objects.requireNonNull(combination.getTrailer().getId());
      // Якщо передали інші ТС поверх связки — override
      if (StringUtils.hasText(tractorId) && !Objects.equals(tractorId, resolvedTractorId)) {
        resolvedTractorId = tractorId;
        resolvedCombinationId = null;
      } else if (StringUtils.hasText(trailerId) && !Objects.equals(trailerId, resolvedTrailerId)) {
        resolvedTrailerId = trailerId;
        resolvedCombinationId = null;
      } else {
        resolvedCombinationId = Objects.requireNonNull(combination.getId());
      }
      if (resolvedCombinationId == null) {
        // override path already set tractor/trailer ids above
      }
    } else if (StringUtils.hasText(tractorId) || StringUtils.hasText(trailerId)) {
      resolvedCombinationId = null;
    }

    if (StringUtils.hasText(resolvedTractorId)) {
      Vehicle tractor =
          vehicleRepository
              .findByIdAndEnterpriseIdAndDeletedFalse(
                  Objects.requireNonNull(resolvedTractorId), currentEnterprise.requireId())
              .orElseThrow(() -> ApiException.notFound("Tractor not found"));
      if (tractor.getVehicleType() != VehicleType.SEMI_TRACTOR) {
        throw ApiException.conflict("INVALID_VEHICLE_TYPE", "Tractor must be SEMI_TRACTOR");
      }
      trip.setTractorId(Objects.requireNonNull(tractor.getId()));
      trip.setTractorPlate(Objects.requireNonNull(tractor.getPlateNumber()));
    } else {
      trip.setTractorId(null);
      trip.setTractorPlate(null);
    }

    if (StringUtils.hasText(resolvedTrailerId)) {
      Vehicle trailer =
          vehicleRepository
              .findByIdAndEnterpriseIdAndDeletedFalse(
                  Objects.requireNonNull(resolvedTrailerId), currentEnterprise.requireId())
              .orElseThrow(() -> ApiException.notFound("Trailer not found"));
      if (trailer.getVehicleType() != VehicleType.SEMI_TRAILER) {
        throw ApiException.conflict("INVALID_VEHICLE_TYPE", "Trailer must be SEMI_TRAILER");
      }
      trip.setTrailerId(Objects.requireNonNull(trailer.getId()));
      trip.setTrailerPlate(Objects.requireNonNull(trailer.getPlateNumber()));
    } else {
      trip.setTrailerId(null);
      trip.setTrailerPlate(null);
    }

    trip.setCombinationId(resolvedCombinationId);
  }

  private void assertReadyForPlanned(Trip trip) {
    if (!StringUtils.hasText(trip.getDriverId())
        || !StringUtils.hasText(trip.getTractorId())
        || !StringUtils.hasText(trip.getTrailerId())
        || trip.getPlannedStartAt() == null
        || trip.getPlannedEndAt() == null) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR",
          "PLANNED requires driver, tractor, trailer and planned start/end dates");
    }
    driverService.requireActiveDriver(Objects.requireNonNull(trip.getDriverId()));
    vehicleRepository
        .findByIdAndEnterpriseIdAndDeletedFalse(
            Objects.requireNonNull(trip.getTractorId()), currentEnterprise.requireId())
        .orElseThrow(() -> ApiException.notFound("Tractor not found"));
    vehicleRepository
        .findByIdAndEnterpriseIdAndDeletedFalse(
            Objects.requireNonNull(trip.getTrailerId()), currentEnterprise.requireId())
        .orElseThrow(() -> ApiException.notFound("Trailer not found"));
  }

  private void assertLicenseValid(Trip trip) {
    Driver driver = driverService.requireActiveDriver(Objects.requireNonNull(trip.getDriverId()));
    LocalDate plannedDay =
        Objects.requireNonNull(trip.getPlannedStartAt()).atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate licenseExpiresOn = driverService.resolveLicenseExpiresOn(driver);
    if (licenseExpiresOn.isBefore(plannedDay)) {
      throw ApiException.conflict("LICENSE_EXPIRED", "Driver license is expired for planned start");
    }
  }

  private void assertNoOverlap(Trip trip) {
    List<Trip> overlaps =
        tripRepository.findOverlapping(
            currentEnterprise.requireId(),
            Objects.requireNonNull(trip.getId()),
            OVERLAP_STATUSES,
            Objects.requireNonNull(trip.getPlannedStartAt()),
            Objects.requireNonNull(trip.getPlannedEndAt()),
            trip.getDriverId(),
            trip.getTractorId(),
            trip.getTrailerId());
    if (!overlaps.isEmpty()) {
      throw ApiException.conflict(
          "RESOURCE_OVERLAP", "Driver or vehicle already assigned to overlapping trip");
    }
  }

  private void assertTransition(TripStatus from, TripStatus to) {
    boolean ok =
        switch (from) {
          case DRAFT -> to == TripStatus.PLANNED || to == TripStatus.CANCELLED;
          case PLANNED ->
              to == TripStatus.IN_PROGRESS
                  || to == TripStatus.COMPLETED
                  || to == TripStatus.CANCELLED;
          case IN_PROGRESS -> to == TripStatus.COMPLETED || to == TripStatus.CANCELLED;
          case COMPLETED, CANCELLED -> false;
        };
    if (!ok) {
      throw ApiException.conflict(
          "INVALID_STATUS_TRANSITION", "Cannot change status from " + from + " to " + to);
    }
  }

  /** Наступний номер рейсу: лічильник окремий для пари (підприємство, рік). */
  private String nextTripNumber(@NonNull String enterpriseId) {
    int year = Year.now(ZoneOffset.UTC).getValue();
    TripNumberSeq seq =
        tripNumberSeqRepository
            .findByEnterpriseIdAndYearForUpdate(enterpriseId, year)
            .orElseGet(
                () -> {
                  TripNumberSeq created = new TripNumberSeq();
                  created.setEnterpriseId(enterpriseId);
                  created.setYear(year);
                  created.setLastValue(0);
                  return tripNumberSeqRepository.saveAndFlush(created);
                });
    // повторне читання з lock після create
    seq =
        tripNumberSeqRepository
            .findByEnterpriseIdAndYearForUpdate(enterpriseId, year)
            .orElseThrow(
                () -> ApiException.conflict("TRIP_NUMBER_SEQ", "Cannot allocate trip number"));
    seq.setLastValue(seq.getLastValue() + 1);
    tripNumberSeqRepository.save(seq);
    return String.format(Locale.ROOT, "TR-%d-%04d", year, seq.getLastValue());
  }

  private static String formatDriverName(@NonNull Driver driver) {
    StringBuilder sb = new StringBuilder();
    sb.append(Objects.requireNonNull(driver.getLastName()))
        .append(' ')
        .append(Objects.requireNonNull(driver.getFirstName()));
    if (StringUtils.hasText(driver.getPatronymic())) {
      sb.append(' ').append(Objects.requireNonNull(driver.getPatronymic()));
    }
    return sb.toString();
  }

  @Nullable
  private static String trimOrNull(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private TripDto toDto(Trip trip) {
    String expenseStatus =
        expenseReportRepository
            .findByTripId(Objects.requireNonNull(trip.getId()))
            .map(r -> Objects.requireNonNull(r.getStatus()).name())
            .orElse(null);
    return new TripDto(
        Objects.requireNonNull(trip.getId()),
        Objects.requireNonNull(trip.getTripNumber()),
        Objects.requireNonNull(trip.getStatus()),
        trip.getRouteRequestId(),
        trip.getTitle(),
        trip.getComment(),
        trip.getOriginText(),
        trip.getDestinationText(),
        trip.getPlannedStartAt(),
        trip.getPlannedEndAt(),
        trip.getActualStartAt(),
        trip.getActualEndAt(),
        trip.getDriverId(),
        trip.getDriverName(),
        trip.getCombinationId(),
        trip.getTractorId(),
        trip.getTractorPlate(),
        trip.getTrailerId(),
        trip.getTrailerPlate(),
        expenseStatus,
        trip.isDeleted(),
        trip.getDeletedAt(),
        trip.getCreatedAt(),
        trip.getUpdatedAt());
  }
}
