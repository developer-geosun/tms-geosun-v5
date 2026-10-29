package com.geosun.tms.trips.repository;

import com.geosun.tms.trips.domain.TripNumberSeq;
import com.geosun.tms.trips.domain.TripNumberSeqId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripNumberSeqRepository extends JpaRepository<TripNumberSeq, TripNumberSeqId> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from TripNumberSeq s where s.enterpriseId = :enterpriseId and s.year = :year")
  Optional<TripNumberSeq> findByEnterpriseIdAndYearForUpdate(
      @Param("enterpriseId") String enterpriseId, @Param("year") int year);
}
