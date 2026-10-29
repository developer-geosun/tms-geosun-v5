package com.geosun.tms.routes.repository;

import com.geosun.tms.routes.domain.Route;
import com.geosun.tms.routes.domain.RouteRequest;
import com.geosun.tms.routes.dto.RouteRequestStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class RouteRequestSpecifications {
  private RouteRequestSpecifications() {}

  public static Specification<RouteRequest> adminFilter(
      String enterpriseId,
      RouteRequestStatus status,
      Instant createdFrom,
      Instant createdTo,
      String ownerEmail,
      String routeTitle) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("enterpriseId"), enterpriseId));
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }
      if (StringUtils.hasText(ownerEmail)) {
        Join<?, ?> user = root.join("user", JoinType.INNER);
        predicates.add(
            cb.like(cb.lower(user.get("email")), "%" + ownerEmail.trim().toLowerCase() + "%"));
      }
      if (StringUtils.hasText(routeTitle)) {
        Join<RouteRequest, Route> route = root.join("route", JoinType.INNER);
        predicates.add(
            cb.like(cb.lower(route.get("title")), "%" + routeTitle.trim().toLowerCase() + "%"));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  public static Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  public static Instant endOfDay(LocalDate date) {
    return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
  }
}
