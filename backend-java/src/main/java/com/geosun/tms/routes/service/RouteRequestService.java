package com.geosun.tms.routes.service;

import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.freight.cost.service.FreightNumericScenarioService;
import com.geosun.tms.routes.domain.Route;
import com.geosun.tms.routes.domain.RoutePoint;
import com.geosun.tms.routes.domain.RouteRequest;
import com.geosun.tms.routes.domain.RouteRequestStatusHistory;
import com.geosun.tms.routes.dto.RoutePointType;
import com.geosun.tms.routes.dto.RouteRequestStatus;
import com.geosun.tms.routes.dto.request.AdminRouteRequestListQuery;
import com.geosun.tms.routes.dto.request.CargoDetailsRequest;
import com.geosun.tms.routes.dto.request.CountryBreakdownRequest;
import com.geosun.tms.routes.dto.request.CreateRouteRequestRequest;
import com.geosun.tms.routes.dto.response.CountryDistanceDto;
import com.geosun.tms.routes.dto.response.PageResponse;
import com.geosun.tms.routes.dto.response.QuoteDto;
import com.geosun.tms.routes.dto.response.RoutePointDto;
import com.geosun.tms.routes.dto.response.RouteRequestDto;
import com.geosun.tms.routes.dto.response.RouteSnapshotDto;
import com.geosun.tms.routes.port.FreightRequestCreatedNotifyPort;
import com.geosun.tms.routes.repository.RouteCountryDistanceRepository;
import com.geosun.tms.routes.repository.RouteRepository;
import com.geosun.tms.routes.repository.RouteRequestRepository;
import com.geosun.tms.routes.repository.RouteRequestSpecifications;
import com.geosun.tms.routes.repository.RouteRequestStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class RouteRequestService {
  private final RouteRepository routeRepository;
  private final RouteRequestRepository routeRequestRepository;
  private final RouteRequestStatusHistoryRepository historyRepository;
  private final CountryBreakdownService countryBreakdownService;
  private final FreightQuoteService freightQuoteService;
  private final RouteCountryDistanceRepository routeCountryDistanceRepository;
  private final FreightNumericScenarioService freightNumericScenarioService;
  private final FreightRequestCreatedNotifyPort freightRequestCreatedNotifyPort;
  private final CurrentEnterprise currentEnterprise;

  public RouteRequestService(
      RouteRepository routeRepository,
      RouteRequestRepository routeRequestRepository,
      RouteRequestStatusHistoryRepository historyRepository,
      CountryBreakdownService countryBreakdownService,
      FreightQuoteService freightQuoteService,
      RouteCountryDistanceRepository routeCountryDistanceRepository,
      FreightNumericScenarioService freightNumericScenarioService,
      FreightRequestCreatedNotifyPort freightRequestCreatedNotifyPort,
      CurrentEnterprise currentEnterprise) {
    this.routeRepository = routeRepository;
    this.routeRequestRepository = routeRequestRepository;
    this.historyRepository = historyRepository;
    this.countryBreakdownService = countryBreakdownService;
    this.freightQuoteService = freightQuoteService;
    this.routeCountryDistanceRepository = routeCountryDistanceRepository;
    this.freightNumericScenarioService = freightNumericScenarioService;
    this.freightRequestCreatedNotifyPort = freightRequestCreatedNotifyPort;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional
  public RouteRequestDto createRouteRequest(String userId, CreateRouteRequestRequest request) {
    Long routeId = parseRouteId(request.routeId());
    String enterpriseId = currentEnterprise.requireId();
    Route route =
        routeRepository
            .findByIdAndEnterpriseIdAndUserIdAndDeletedFalse(routeId, enterpriseId, userId)
            .orElseThrow(() -> ApiException.notFound("Route not found"));

    RouteRequest routeRequest = new RouteRequest();
    routeRequest.setEnterpriseId(enterpriseId);
    routeRequest.setUser(route.getUser());
    routeRequest.setRoute(route);
    routeRequest.setStatus(RouteRequestStatus.NEW);
    routeRequest.setComment(request.comment());
    routeRequest.setPreferredStartDate(parseDateOrNull(request.preferredStartDate()));
    applyCargo(routeRequest, request.cargo());
    RouteRequest saved = routeRequestRepository.save(routeRequest);

    RouteRequestStatusHistory history = new RouteRequestStatusHistory();
    history.setRequest(saved);
    history.setFromStatus(null);
    history.setToStatus(RouteRequestStatus.NEW);
    history.setChangedBy(route.getUser());
    history.setNote("Created by user");
    historyRepository.save(history);

    scheduleFreightRequestNotifyAfterCommit(saved);

    return toDto(saved, true);
  }

  /** Сповіщення ADMIN лише після успішного commit створення заявки. */
  private void scheduleFreightRequestNotifyAfterCommit(@NonNull RouteRequest saved) {
    Long requestId = Objects.requireNonNull(saved.getId());
    var author = Objects.requireNonNull(saved.getUser());
    String authorUserId = Objects.requireNonNull(author.getId());
    String authorEmail =
        StringUtils.hasText(author.getEmail())
            ? Objects.requireNonNull(author.getEmail().trim())
            : "";
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      freightRequestCreatedNotifyPort.notifyCreated(requestId, authorUserId, authorEmail);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            freightRequestCreatedNotifyPort.notifyCreated(requestId, authorUserId, authorEmail);
          }
        });
  }

  @Transactional(readOnly = true)
  public List<RouteRequestDto> getMyRouteRequests(String userId) {
    return routeRequestRepository
        .findByEnterpriseIdAndUserIdOrderByCreatedAtDesc(currentEnterprise.requireId(), userId)
        .stream()
        .map((request) -> toDto(request, false))
        .toList();
  }

  @Transactional(readOnly = true)
  public RouteRequestDto getMyRouteRequestById(String userId, Long requestId) {
    RouteRequest request =
        routeRequestRepository
            .findByIdAndEnterpriseIdAndUserId(requestId, currentEnterprise.requireId(), userId)
            .orElseThrow(() -> ApiException.notFound("Route request not found"));
    return toDto(request, true);
  }

  @Transactional(readOnly = true)
  public PageResponse<RouteRequestDto> getAllRequestsForAdmin(AdminRouteRequestListQuery query) {
    int page = Math.max(0, query.page());
    int size = Math.min(100, Math.max(1, query.size()));
    Sort sort = resolveAdminSort(query.sort(), query.order());
    Specification<RouteRequest> spec =
        RouteRequestSpecifications.adminFilter(
            currentEnterprise.requireId(),
            query.status(),
            parseInstantDate(query.createdFrom(), true),
            parseInstantDate(query.createdTo(), false),
            query.ownerEmail(),
            query.routeTitle());
    Page<RouteRequest> result =
        routeRequestRepository.findAll(spec, PageRequest.of(page, size, sort));
    List<RouteRequestDto> content =
        result.getContent().stream().map((request) -> toDto(request, false)).toList();
    return new PageResponse<>(
        content,
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize());
  }

  /** Довідник email власників заявок для випадаючого списку у фільтрі адмінки. */
  @Transactional(readOnly = true)
  public List<String> getOwnerEmailsForAdmin() {
    return routeRequestRepository.findDistinctOwnerEmails(currentEnterprise.requireId()).stream()
        .filter(StringUtils::hasText)
        .map(email -> email.trim())
        .distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
  }

  @Transactional(readOnly = true)
  public RouteRequestDto getRequestByIdForAdmin(Long requestId) {
    if (requestId == null) {
      throw new IllegalArgumentException("requestId must not be null");
    }
    RouteRequest request =
        routeRequestRepository
            .findByIdAndEnterpriseId(
                Objects.requireNonNull(requestId), currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Route request not found"));
    return toDto(request, true);
  }

  /** Явний перерахунок пробігу по країнах (HERE) для адмінки; ТЗ §3.3. */
  @Transactional
  public RouteRequestDto recalculateCountryBreakdownForAdmin(
      Long requestId, CountryBreakdownRequest body) {
    if (requestId == null) {
      throw new IllegalArgumentException("requestId must not be null");
    }
    RouteRequest request =
        routeRequestRepository
            .findByIdAndEnterpriseId(
                Objects.requireNonNull(requestId), currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Route request not found"));

    if (body != null && StringUtils.hasText(body.scenarioId())) {
      String scenarioId = body.scenarioId().trim();
      freightNumericScenarioService.loadScenario(scenarioId);
      String previousScenarioId = request.getNbuBreakdownScenarioId();
      if (previousScenarioId != null && !previousScenarioId.equals(scenarioId)) {
        routeCountryDistanceRepository.deleteByRouteId(request.getRoute().getId());
      }
      request.setNbuBreakdownScenarioId(scenarioId);
      request.setNbuBreakdownAt(Instant.now());
      routeRequestRepository.save(request);
    }

    countryBreakdownService.getOrCalculate(request.getRoute());
    return toDto(request, true);
  }

  private RouteRequestDto toDto(RouteRequest request, boolean includeRoutePoints) {
    Long requestId = request.getId();
    if (requestId == null) {
      throw new IllegalStateException("Route request id must not be null");
    }
    RouteSnapshotDto route =
        includeRoutePoints
            ? toRouteSnapshot(request.getRoute())
            : toRouteSummaryAsSnapshot(request.getRoute());
    List<CountryDistanceDto> countryDistances =
        countryBreakdownService.listStoredOnly(request.getRoute());
    QuoteDto currentQuote = freightQuoteService.getCurrentQuoteForRequest(requestId);
    String requesterEmail =
        request.getUser() == null || !StringUtils.hasText(request.getUser().getEmail())
            ? null
            : request.getUser().getEmail().trim();
    return new RouteRequestDto(
        request.getId(),
        String.valueOf(request.getRoute().getId()),
        request.getStatus(),
        request.getPreferredStartDate() == null ? null : request.getPreferredStartDate().toString(),
        request.getComment(),
        requesterEmail,
        request.getCreatedAt() == null ? null : request.getCreatedAt().toString(),
        request.getUpdatedAt() == null ? null : request.getUpdatedAt().toString(),
        route,
        countryDistances,
        currentQuote);
  }

  private RouteSnapshotDto toRouteSummaryAsSnapshot(Route route) {
    boolean locked = routeRequestRepository.existsByRoute_Id(route.getId());
    return new RouteSnapshotDto(
        String.valueOf(route.getId()),
        route.getTitle(),
        route.getRoutingProfile(),
        route.getRoutingMode(),
        route.getRoutePolyline(),
        toDouble(route.getDistanceKm()),
        route.getDurationMin(),
        route.getRouteComment(),
        route.getCreatedAt() == null ? null : route.getCreatedAt().toString(),
        route.getUpdatedAt() == null ? null : route.getUpdatedAt().toString(),
        List.of(),
        locked);
  }

  private RouteSnapshotDto toRouteSnapshot(Route route) {
    boolean locked = routeRequestRepository.existsByRoute_Id(route.getId());
    List<RoutePointDto> points =
        route.getPoints() == null
            ? List.of()
            : route.getPoints().stream()
                .sorted(
                    Comparator.comparing(
                        (RoutePoint point) -> Objects.requireNonNull(point.getPointOrder())))
                .map(
                    point ->
                        new RoutePointDto(
                            point.getPointOrder(),
                            RoutePointType.valueOf(point.getPointType().name()),
                            point.getAddress(),
                            toDouble(point.getLat()),
                            toDouble(point.getLng()),
                            point.getCountry(),
                            point.isBorder(),
                            toDouble(point.getSegmentDistanceKmToNext()),
                            RouteService.toDtoOperations(point.getOperations())))
                .toList();

    return new RouteSnapshotDto(
        String.valueOf(route.getId()),
        route.getTitle(),
        route.getRoutingProfile(),
        route.getRoutingMode(),
        route.getRoutePolyline(),
        toDouble(route.getDistanceKm()),
        route.getDurationMin(),
        route.getRouteComment(),
        route.getCreatedAt() == null ? null : route.getCreatedAt().toString(),
        route.getUpdatedAt() == null ? null : route.getUpdatedAt().toString(),
        points,
        locked);
  }

  @NonNull
  private static Sort resolveAdminSort(String sortField, String order) {
    String field =
        switch (sortField == null ? "" : sortField) {
          case "status" -> "status";
          case "preferredStartDate" -> "preferredStartDate";
          default -> "createdAt";
        };
    Sort.Direction direction =
        "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }

  private static Instant parseInstantDate(String raw, boolean startOfDay) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(raw.trim());
      return startOfDay
          ? RouteRequestSpecifications.startOfDay(date)
          : RouteRequestSpecifications.endOfDay(date);
    } catch (DateTimeParseException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid date filter format");
    }
  }

  private LocalDate parseDateOrNull(String rawDate) {
    if (rawDate == null || rawDate.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(rawDate);
    } catch (DateTimeParseException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid preferredStartDate format");
    }
  }

  private Long parseRouteId(String routeId) {
    try {
      return Long.parseLong(Objects.requireNonNull(routeId, "routeId must not be null"));
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Route id must be numeric");
    }
  }

  private static void applyCargo(RouteRequest routeRequest, CargoDetailsRequest cargo) {
    if (cargo == null) {
      return;
    }
    routeRequest.setCargoType(cargo.type());
    routeRequest.setWeightKg(toBigDecimal(cargo.weightKg()));
    routeRequest.setVolumeM3(toBigDecimal(cargo.volumeM3()));
  }

  private static BigDecimal toBigDecimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private static Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
