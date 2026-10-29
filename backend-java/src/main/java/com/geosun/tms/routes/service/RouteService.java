package com.geosun.tms.routes.service;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.routes.domain.Route;
import com.geosun.tms.routes.domain.RoutePoint;
import com.geosun.tms.routes.domain.RoutePointKind;
import com.geosun.tms.routes.domain.RoutePointOperation;
import com.geosun.tms.routes.domain.RoutePointOperationsRules;
import com.geosun.tms.routes.domain.RoutePointOperationsRules.RoutePointWithOperations;
import com.geosun.tms.routes.domain.RoutePointOperationsRules.ValidationError;
import com.geosun.tms.routes.dto.RouteListView;
import com.geosun.tms.routes.dto.RoutePointOperationDto;
import com.geosun.tms.routes.dto.RoutePointType;
import com.geosun.tms.routes.dto.request.CreateRouteRequestRequest;
import com.geosun.tms.routes.dto.request.RoutePointRequest;
import com.geosun.tms.routes.dto.request.SaveRouteRequest;
import com.geosun.tms.routes.dto.response.RoutePointDto;
import com.geosun.tms.routes.dto.response.RouteRequestDto;
import com.geosun.tms.routes.dto.response.RouteSnapshotDto;
import com.geosun.tms.routes.dto.response.RouteSummaryDto;
import com.geosun.tms.routes.repository.RouteCountryDistanceRepository;
import com.geosun.tms.routes.repository.RouteRepository;
import com.geosun.tms.routes.repository.RouteRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService implements RouteContractsFacade {
  /** Суфікс назви для дубліката маршруту (ТЗ §3.1). */
  private static final String DUPLICATE_ROUTE_TITLE_SUFFIX = " (копія)";

  private final RouteRepository routeRepository;
  private final RouteCountryDistanceRepository routeCountryDistanceRepository;
  private final UserRepository userRepository;
  private final RouteRequestRepository routeRequestRepository;
  private final RouteRequestService routeRequestService;
  private final CurrentEnterprise currentEnterprise;

  public RouteService(
      RouteRepository routeRepository,
      RouteCountryDistanceRepository routeCountryDistanceRepository,
      UserRepository userRepository,
      RouteRequestRepository routeRequestRepository,
      RouteRequestService routeRequestService,
      CurrentEnterprise currentEnterprise) {
    this.routeRepository = routeRepository;
    this.routeCountryDistanceRepository = routeCountryDistanceRepository;
    this.userRepository = userRepository;
    this.routeRequestRepository = routeRequestRepository;
    this.routeRequestService = routeRequestService;
    this.currentEnterprise = currentEnterprise;
  }

  @Override
  @Transactional
  public RouteSnapshotDto saveRoute(String userId, SaveRouteRequest request) {
    validatePoints(request.points());
    if (userId == null) {
      throw new IllegalArgumentException("userId must not be null");
    }
    String enterpriseId = currentEnterprise.requireId();
    User user =
        userRepository
            .findById(userId)
            .filter(u -> enterpriseId.equals(u.getEnterpriseId()))
            .orElseThrow(() -> ApiException.notFound("User not found"));

    Route route = new Route();
    route.setEnterpriseId(enterpriseId);
    route.setUser(user);
    route.setTitle(request.title());
    route.setRoutingProfile(request.routingProfile());
    route.setRoutingMode(request.routingMode());
    route.setRoutePolyline(request.routePolyline());
    route.setDistanceKm(toBigDecimal(request.distanceKm()));
    route.setDurationMin(request.durationMin());
    route.setRouteComment(request.routeComment());
    route.setPoints(
        new ArrayList<>(
            request.points().stream()
                .sorted(
                    Comparator.comparing(
                        (RoutePointRequest point) -> Objects.requireNonNull(point.order())))
                .map((point) -> toEntityPoint(route, point))
                .toList()));

    Route saved = routeRepository.save(route);
    return toSnapshot(saved);
  }

  /** Дублікат маршруту з новим id (ТЗ §3.1); лише не видалений маршрут. */
  @Transactional
  public RouteSnapshotDto duplicateMyRoute(String userId, Long routeId) {
    Route source =
        routeRepository
            .findByIdAndUserIdWithPoints(routeId, currentEnterprise.requireId(), userId)
            .orElseThrow(() -> ApiException.notFound("Route not found"));
    Route copy = new Route();
    copy.setEnterpriseId(currentEnterprise.requireId());
    copy.setUser(source.getUser());
    copy.setTitle(source.getTitle() + DUPLICATE_ROUTE_TITLE_SUFFIX);
    copy.setRoutingProfile(source.getRoutingProfile());
    copy.setRoutingMode(source.getRoutingMode());
    copy.setRoutePolyline(source.getRoutePolyline());
    copy.setDistanceKm(source.getDistanceKm());
    copy.setDurationMin(source.getDurationMin());
    copy.setRouteComment(source.getRouteComment());
    copy.setDeleted(false);
    copy.setDeletedAt(null);
    copy.setPoints(copyRoutePointsFrom(source, copy));
    Route saved = routeRepository.save(copy);
    return toSnapshot(saved);
  }

  /** Скасування soft delete (ТЗ §5.2.1); ідемпотентність якщо вже активний. */
  @Transactional
  public RouteSnapshotDto restoreMyRoute(String userId, Long routeId) {
    Route route =
        routeRepository
            .findByIdAndUserIdWithPointsIncludingDeleted(
                routeId, currentEnterprise.requireId(), userId)
            .orElseThrow(() -> ApiException.notFound("Route not found"));
    if (!route.isDeleted()) {
      return toSnapshot(route);
    }
    route.setDeleted(false);
    route.setDeletedAt(null);
    routeRepository.save(route);
    return toSnapshot(route);
  }

  @Override
  @Transactional
  public RouteSnapshotDto updateMyRoute(String userId, Long routeId, SaveRouteRequest request) {
    validatePoints(request.points());
    if (userId == null) {
      throw new IllegalArgumentException("userId must not be null");
    }
    Route route =
        routeRepository
            .findByIdAndUserIdWithPoints(routeId, currentEnterprise.requireId(), userId)
            .orElseThrow(() -> ApiException.notFound("Route not found"));
    if (routeRequestRepository.existsByRoute_Id(routeId)) {
      throw ApiException.conflict(
          "ROUTE_LOCKED_BY_REQUEST",
          "Route cannot be edited after a freight request exists; duplicate the route to edit.");
    }

    route.setTitle(request.title());
    route.setRoutingProfile(request.routingProfile());
    route.setRoutingMode(request.routingMode());
    route.setRoutePolyline(request.routePolyline());
    route.setDistanceKm(toBigDecimal(request.distanceKm()));
    route.setDurationMin(request.durationMin());
    route.setRouteComment(request.routeComment());

    // Замінюємо колекцію точок маршруту повністю; завдяки orphanRemoval=true
    // старі записи видаляються автоматично.
    List<RoutePoint> newPoints =
        request.points().stream()
            .sorted(
                Comparator.comparing(
                    (RoutePointRequest point) -> Objects.requireNonNull(point.order())))
            .map((point) -> toEntityPoint(route, point))
            .toList();
    route.getPoints().clear();
    // Примусово виконуємо DELETE старих точок до INSERT нових, аби не зачепити
    // унікальний індекс (route_id, point_order).
    routeRepository.flush();
    route.getPoints().addAll(new ArrayList<>(newPoints));

    // Після зміни точок перерахунок пробігу по країнах має виконуватись заново.
    routeCountryDistanceRepository.deleteByRouteId(route.getId());

    return toSnapshot(route);
  }

  @Override
  @Transactional(readOnly = true)
  public List<RouteSummaryDto> getMyRoutes(String userId, RouteListView view) {
    String enterpriseId = currentEnterprise.requireId();
    List<Route> routes =
        switch (view) {
          case ACTIVE ->
              routeRepository.findByEnterpriseIdAndUserIdAndDeletedFalseOrderByUpdatedAtDesc(
                  enterpriseId, userId);
          case DELETED ->
              routeRepository.findByEnterpriseIdAndUserIdAndDeletedTrueOrderByUpdatedAtDesc(
                  enterpriseId, userId);
          case ALL ->
              routeRepository.findByEnterpriseIdAndUserIdOrderByUpdatedAtDesc(enterpriseId, userId);
        };
    return routes.stream().map(this::toSummary).toList();
  }

  @Override
  @Transactional
  public RouteSnapshotDto getMyRouteById(String userId, Long routeId) {
    String enterpriseId = currentEnterprise.requireId();
    Route route =
        routeRepository
            .findByIdAndUserIdWithPointsIncludingDeleted(routeId, enterpriseId, userId)
            .orElseThrow(() -> ApiException.notFound("Route not found"));
    if (!route.isDeleted()) {
      routeRepository.updateLastOpenedAt(routeId, enterpriseId, userId, Instant.now());
    }
    return toSnapshot(route);
  }

  @Transactional
  public void softDeleteMyRoute(String userId, Long routeId) {
    Route route =
        routeRepository
            .findByIdAndEnterpriseIdAndUserIdAndDeletedFalse(
                routeId, currentEnterprise.requireId(), userId)
            .orElseThrow(() -> ApiException.notFound("Route not found"));
    route.setDeleted(true);
    route.setDeletedAt(Instant.now());
  }

  @Override
  public RouteRequestDto createRouteRequest(String userId, CreateRouteRequestRequest request) {
    return routeRequestService.createRouteRequest(userId, request);
  }

  @Override
  public List<RouteRequestDto> getMyRouteRequests(String userId) {
    return routeRequestService.getMyRouteRequests(userId);
  }

  private static List<RoutePoint> copyRoutePointsFrom(Route source, Route target) {
    if (source.getPoints() == null || source.getPoints().isEmpty()) {
      return new ArrayList<>();
    }
    List<RoutePoint> copies = new ArrayList<>();
    for (RoutePoint p :
        source.getPoints().stream()
            .sorted(
                Comparator.comparing(
                    (RoutePoint point) -> Objects.requireNonNull(point.getPointOrder())))
            .toList()) {
      RoutePoint c = new RoutePoint();
      c.setRoute(target);
      c.setPointOrder(p.getPointOrder());
      c.setPointType(p.getPointType());
      c.setOperations(
          p.getOperations() == null ? new ArrayList<>() : new ArrayList<>(p.getOperations()));
      c.setAddress(p.getAddress());
      c.setLat(p.getLat());
      c.setLng(p.getLng());
      c.setCountry(p.getCountry());
      c.setBorder(p.isBorder());
      c.setSegmentDistanceKmToNext(p.getSegmentDistanceKmToNext());
      copies.add(c);
    }
    return copies;
  }

  private static RoutePoint toEntityPoint(Route route, RoutePointRequest request) {
    RoutePoint point = new RoutePoint();
    point.setRoute(route);
    point.setPointOrder(request.order());
    point.setPointType(RoutePointKind.valueOf(request.type().name()));
    point.setAddress(request.address());
    point.setLat(toBigDecimal(request.lat()));
    point.setLng(toBigDecimal(request.lng()));
    point.setCountry(request.country());
    point.setBorder(Boolean.TRUE.equals(request.isBorder()));
    point.setSegmentDistanceKmToNext(toBigDecimal(request.segmentDistanceKmToNext()));
    point.setOperations(toDomainOperations(request.operations()));
    return point;
  }

  private static List<RoutePointOperation> toDomainOperations(
      List<RoutePointOperationDto> operations) {
    if (operations == null || operations.isEmpty()) {
      return new ArrayList<>();
    }
    List<RoutePointOperation> result = new ArrayList<>(operations.size());
    for (RoutePointOperationDto op : operations) {
      if (op != null) {
        result.add(RoutePointOperation.valueOf(op.name()));
      }
    }
    return result;
  }

  static List<RoutePointOperationDto> toDtoOperations(List<RoutePointOperation> operations) {
    if (operations == null || operations.isEmpty()) {
      return List.of();
    }
    List<RoutePointOperationDto> result = new ArrayList<>(operations.size());
    for (RoutePointOperation op : operations) {
      if (op != null) {
        result.add(RoutePointOperationDto.valueOf(op.name()));
      }
    }
    return result;
  }

  private RouteSummaryDto toSummary(Route route) {
    String createdAt =
        route.getCreatedAt() != null
            ? route.getCreatedAt().toString()
            : (route.getUpdatedAt() == null ? null : route.getUpdatedAt().toString());
    return new RouteSummaryDto(
        String.valueOf(route.getId()),
        route.getTitle(),
        toDouble(route.getDistanceKm()),
        route.getDurationMin(),
        route.getPoints() == null ? 0 : route.getPoints().size(),
        createdAt,
        route.getUpdatedAt() == null ? null : route.getUpdatedAt().toString(),
        route.getLastOpenedAt() == null ? null : route.getLastOpenedAt().toString(),
        routeRequestRepository.existsByRoute_Id(route.getId()),
        route.isDeleted());
  }

  private RouteSnapshotDto toSnapshot(Route route) {
    List<RoutePointDto> points =
        route.getPoints() == null
            ? List.of()
            : route.getPoints().stream()
                .sorted(
                    Comparator.comparing(
                        (RoutePoint point) -> Objects.requireNonNull(point.getPointOrder())))
                .map(this::toPointDto)
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
        routeRequestRepository.existsByRoute_Id(route.getId()));
  }

  private RoutePointDto toPointDto(RoutePoint point) {
    RoutePointType type = RoutePointType.valueOf(point.getPointType().name());
    return new RoutePointDto(
        point.getPointOrder(),
        type,
        point.getAddress(),
        toDouble(point.getLat()),
        toDouble(point.getLng()),
        point.getCountry(),
        point.isBorder(),
        toDouble(point.getSegmentDistanceKmToNext()),
        toDtoOperations(point.getOperations()));
  }

  private static void validatePoints(List<RoutePointRequest> points) {
    if (points == null || points.size() < 2) {
      throw ApiException.badRequest("ROUTE_POINTS_INVALID", "Route must contain at least 2 points");
    }
    validateOperations(points);
  }

  private static void validateOperations(List<RoutePointRequest> points) {
    List<RoutePointRequest> orderedRequests =
        points.stream()
            .sorted(
                Comparator.comparing(
                    (RoutePointRequest point) -> Objects.requireNonNull(point.order())))
            .toList();
    List<RoutePointWithOperations> ordered =
        orderedRequests.stream().map(RouteService::toValidationPoint).toList();
    ValidationError error = RoutePointOperationsRules.validateRoute(ordered);
    if (error == null) {
      return;
    }
    if (shouldIgnoreOperationSetInvalidForCargoPair(error, orderedRequests)) {
      return;
    }
    String message = buildOperationsValidationMessage(error, orderedRequests);
    throw ApiException.badRequest("ROUTE_OPERATIONS_" + error.code().name(), message);
  }

  private static boolean shouldIgnoreOperationSetInvalidForCargoPair(
      ValidationError error, List<RoutePointRequest> orderedRequests) {
    if (error.code() != RoutePointOperationsRules.ValidationErrorCode.OPERATION_SET_INVALID) {
      return false;
    }
    int index = error.pointIndex();
    if (index < 0 || index >= orderedRequests.size()) {
      return false;
    }
    RoutePointRequest point = orderedRequests.get(index);
    if (point.type() == RoutePointType.BORDER || Boolean.TRUE.equals(point.isBorder())) {
      return false;
    }
    if (point.operations() == null || point.operations().size() != 2) {
      return false;
    }
    // Дозволяємо комбінацію LOADING+UNLOADING на не-border точці навіть якщо старі правила
    // повернули OPERATION_SET_INVALID.
    Set<RoutePointOperationDto> ops = EnumSet.copyOf(point.operations());
    return ops.contains(RoutePointOperationDto.LOADING)
        && ops.contains(RoutePointOperationDto.UNLOADING);
  }

  private static String buildOperationsValidationMessage(
      ValidationError error, List<RoutePointRequest> orderedRequests) {
    if (error.pointIndex() < 0 || error.pointIndex() >= orderedRequests.size()) {
      return "Invalid route point operations";
    }
    RoutePointRequest request = orderedRequests.get(error.pointIndex());
    return "Invalid route point operations at index "
        + error.pointIndex()
        + " (type="
        + request.type()
        + ", isBorder="
        + request.isBorder()
        + ", operations="
        + request.operations()
        + ")";
  }

  private static RoutePointWithOperations toValidationPoint(RoutePointRequest request) {
    Set<RoutePointOperation> ops = EnumSet.noneOf(RoutePointOperation.class);
    if (request.operations() != null) {
      for (RoutePointOperationDto op : request.operations()) {
        if (op != null) {
          ops.add(RoutePointOperation.valueOf(op.name()));
        }
      }
    }
    return new RoutePointWithOperations(RoutePointKind.valueOf(request.type().name()), ops);
  }

  private static BigDecimal toBigDecimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private static Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
