package com.geosun.tms.routes.repository;

import com.geosun.tms.routes.domain.FreightQuote;
import com.geosun.tms.routes.dto.QuoteStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreightQuoteRepository extends JpaRepository<FreightQuote, String> {
  Optional<FreightQuote> findByIdAndEnterpriseId(String id, String enterpriseId);

  List<FreightQuote> findByRequest_IdOrderByCreatedAtDesc(Long requestId);

  Optional<FreightQuote> findFirstByRequest_IdAndStatusInOrderByCreatedAtDesc(
      Long requestId, Collection<QuoteStatus> statuses);

  List<FreightQuote> findByRequest_IdAndStatus(Long requestId, QuoteStatus status);

  List<FreightQuote> findByFreightCostCalculation_Id(String calculationId);
}
