package com.geosun.tms.enterprise.repository;

import com.geosun.tms.enterprise.domain.PlatformOperatorRefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOperatorRefreshTokenRepository
    extends JpaRepository<PlatformOperatorRefreshToken, String> {

  @Query("select r from PlatformOperatorRefreshToken r join fetch r.operator where r.id = :id")
  Optional<PlatformOperatorRefreshToken> findByIdWithOperator(@Param("id") String id);

  @Query(
      "select r from PlatformOperatorRefreshToken r join fetch r.operator"
          + " where r.tokenHash = :hash")
  Optional<PlatformOperatorRefreshToken> findByTokenHashWithOperator(
      @Param("hash") String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PlatformOperatorRefreshToken r set r.revokedAt = :ts"
          + " where r.operator.id = :operatorId and r.revokedAt is null")
  int revokeAllActiveByOperatorId(@Param("operatorId") String operatorId, @Param("ts") Instant ts);
}
