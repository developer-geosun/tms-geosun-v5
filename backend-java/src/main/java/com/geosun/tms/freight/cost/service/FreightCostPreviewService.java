package com.geosun.tms.freight.cost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.freight.cost.domain.DriverSalaryBasis;
import com.geosun.tms.freight.cost.domain.FreightCostCalculation;
import com.geosun.tms.freight.cost.domain.FreightNumericScenario;
import com.geosun.tms.freight.cost.dto.request.CostPreviewRequest;
import com.geosun.tms.freight.cost.dto.response.CostPreviewResponse;
import com.geosun.tms.freight.cost.dto.response.FreightCostCalculationDto;
import com.geosun.tms.freight.cost.dto.response.FreightCostCalculationSummaryDto;
import com.geosun.tms.freight.cost.repository.CountryTollRuleRepository;
import com.geosun.tms.freight.cost.repository.FreightCostCalculationRepository;
import com.geosun.tms.reference.dto.response.NbuRatesSnapshotDto;
import com.geosun.tms.reference.service.NbuExchangeRateService;
import com.geosun.tms.routes.domain.FreightQuote;
import com.geosun.tms.routes.domain.RouteRequest;
import com.geosun.tms.routes.dto.response.CountryDistanceDto;
import com.geosun.tms.routes.repository.FreightQuoteRepository;
import com.geosun.tms.routes.repository.RouteCountryDistanceRepository;
import com.geosun.tms.routes.repository.RouteRequestRepository;
import com.geosun.tms.routes.service.CountryBreakdownService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreightCostPreviewService {
  private final RouteRequestRepository routeRequestRepository;
  private final FreightNumericScenarioService scenarioService;
  private final FreightCostCalculationRepository calculationRepository;
  private final CountryBreakdownService countryBreakdownService;
  private final RouteCountryDistanceRepository routeCountryDistanceRepository;
  private final FreightRouteLengthService routeLengthService;
  private final FreightCostCalculatorService calculatorService;
  private final FreightCostCalculationSummaryBuilder summaryBuilder;
  private final NbuExchangeRateService nbuExchangeRateService;
  private final CountryTollRuleRepository countryTollRuleRepository;
  private final UserRepository userRepository;
  private final FreightQuoteRepository freightQuoteRepository;
  private final ObjectMapper objectMapper;
  private final CurrentEnterprise currentEnterprise;

  public FreightCostPreviewService(
      RouteRequestRepository routeRequestRepository,
      FreightNumericScenarioService scenarioService,
      FreightCostCalculationRepository calculationRepository,
      CountryBreakdownService countryBreakdownService,
      RouteCountryDistanceRepository routeCountryDistanceRepository,
      FreightRouteLengthService routeLengthService,
      FreightCostCalculatorService calculatorService,
      FreightCostCalculationSummaryBuilder summaryBuilder,
      NbuExchangeRateService nbuExchangeRateService,
      CountryTollRuleRepository countryTollRuleRepository,
      UserRepository userRepository,
      FreightQuoteRepository freightQuoteRepository,
      ObjectMapper objectMapper,
      CurrentEnterprise currentEnterprise) {
    this.currentEnterprise = currentEnterprise;
    this.routeRequestRepository = routeRequestRepository;
    this.scenarioService = scenarioService;
    this.calculationRepository = calculationRepository;
    this.countryBreakdownService = countryBreakdownService;
    this.routeCountryDistanceRepository = routeCountryDistanceRepository;
    this.routeLengthService = routeLengthService;
    this.calculatorService = calculatorService;
    this.summaryBuilder = summaryBuilder;
    this.nbuExchangeRateService = nbuExchangeRateService;
    this.countryTollRuleRepository = countryTollRuleRepository;
    this.userRepository = userRepository;
    this.freightQuoteRepository = freightQuoteRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public CostPreviewResponse preview(String userId, Long requestId, CostPreviewRequest request) {
    RouteRequest routeRequest = loadRouteRequest(requestId);
    FreightNumericScenario scenario = scenarioService.loadScenario(request.scenarioId());
    if (!scenario.isActive()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Scenario is not active");
    }
    // Перед розрахунком фрахту завжди оновлюємо пробіг по країнах під обраний scenarioId
    List<CountryDistanceDto> countryDistances =
        recalculateCountryBreakdownForScenario(routeRequest, scenario.getId());
    if (countryDistances.isEmpty()) {
      throw ApiException.unprocessableEntity(
          "COUNTRY_BREAKDOWN_REQUIRED",
          "Не вдалося розрахувати пробіг по країнах для маршруту заявки");
    }

    FreightRouteLengthService.StartPoint startPoint = resolveStartPoint(request.startPoint());
    RouteLengths lengths = routeLengthService.compute(routeRequest.getRoute(), startPoint);
    BigDecimal preRouteEmptyKm = lengths.preRouteEmptyKm();
    NbuRatesSnapshotDto nbuRates =
        nbuExchangeRateService.getRatesForDate(request.calculationDate());

    FreightCostCalculationSummaryDto summary =
        calculatorService.calculate(
            scenario,
            lengths,
            preRouteEmptyKm,
            countryDistances,
            nbuRates,
            request.calculationDate(),
            routeRequest.getPreferredStartDate(),
            request.seasonOverride());

    String calculationSummary = summaryBuilder.build(summary, routeRequest.getRoute(), startPoint);
    JsonNode breakdown = buildBreakdownJson(summary, scenario.getId(), request.calculationDate());
    String scenarioSnapshot = writeJson(scenarioSnapshot(scenario));
    String tollSnapshot = writeJson(tollSetSnapshot(scenario.getTollTariffSet().getId()));
    String nbuSnapshot = writeJson(nbuRates);

    User user = loadUser(userId);
    FreightCostCalculation entity = new FreightCostCalculation();
    entity.setEnterpriseId(currentEnterprise.requireId());
    entity.setRouteRequest(routeRequest);
    entity.setScenario(scenario);
    entity.setCalculationDate(request.calculationDate());
    entity.setBreakdownJson(writeJson(breakdown));
    entity.setCalculationSummary(calculationSummary);
    entity.setScenarioSnapshotJson(scenarioSnapshot);
    entity.setTollTariffSetSnapshotJson(tollSnapshot);
    entity.setNbuRatesSnapshotJson(nbuSnapshot);
    entity.setSeasonUsed(summary.seasonUsed());
    entity.setLTotalKm(summary.lTotalKm());
    entity.setLEmptyKm(summary.lEmptyKm());
    entity.setLLoadedKm(summary.lLoadedKm());
    entity.setDirectCostUah(summary.directCostUah());
    entity.setDriverCostUah(summary.driverCostUah());
    entity.setCostBeforeMarginUah(summary.costBeforeMarginUah());
    entity.setMarginUah(summary.marginUah());
    entity.setTotalUah(summary.totalUah());
    entity.setTotalProposalAmount(summary.totalProposalAmount());
    entity.setProposalCurrency(summary.proposalCurrency());
    entity.setCreatedBy(user);
    FreightCostCalculation saved = calculationRepository.save(entity);

    return new CostPreviewResponse(
        saved.getId(),
        requestId,
        scenario.getId(),
        saved.getCalculationDate(),
        saved.getSeasonUsed(),
        saved.getLTotalKm(),
        saved.getLEmptyKm(),
        saved.getLLoadedKm(),
        saved.getDirectCostUah(),
        saved.getDriverCostUah(),
        saved.getCostBeforeMarginUah(),
        saved.getMarginUah(),
        saved.getTotalUah(),
        saved.getTotalProposalAmount(),
        saved.getProposalCurrency(),
        breakdown,
        calculationSummary,
        saved.getCreatedAt() == null ? null : saved.getCreatedAt().toString());
  }

  @Transactional(readOnly = true)
  public List<FreightCostCalculationDto> listForRequest(Long requestId) {
    loadRouteRequest(requestId);
    return calculationRepository
        .findByEnterpriseIdAndRouteRequest_IdOrderByCreatedAtDesc(
            currentEnterprise.requireId(), requestId)
        .stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public FreightCostCalculationDto getById(Long requestId, String calculationId) {
    loadRouteRequest(requestId);
    FreightCostCalculation calculation =
        calculationRepository
            .findByIdAndEnterpriseIdAndRouteRequest_Id(
                calculationId, currentEnterprise.requireId(), requestId)
            .orElseThrow(() -> ApiException.notFound("Cost calculation not found"));
    return toDto(calculation);
  }

  @Transactional
  public void delete(Long requestId, String calculationId) {
    loadRouteRequest(requestId);
    FreightCostCalculation calculation =
        calculationRepository
            .findByIdAndEnterpriseIdAndRouteRequest_Id(
                calculationId, currentEnterprise.requireId(), requestId)
            .orElseThrow(() -> ApiException.notFound("Cost calculation not found"));
    // Як ON DELETE SET NULL у MySQL: відв'язуємо quotes перед delete (H2/JPA-сесія).
    for (FreightQuote quote :
        freightQuoteRepository.findByFreightCostCalculation_Id(calculationId)) {
      quote.setFreightCostCalculation(null);
    }
    if (calculation == null) {
      throw new NullPointerException("calculation");
    }
    calculationRepository.delete(calculation);
  }

  /**
   * Примусово перераховує пробіг по країнах (HERE/GeoJSON) і фіксує scenarioId на заявці. Викликається
   * з cost-preview — окрема кнопка «Пробіг по країнах» у UI більше не потрібна.
   */
  private List<CountryDistanceDto> recalculateCountryBreakdownForScenario(
      RouteRequest routeRequest, String scenarioId) {
    Long routeId = routeRequest.getRoute().getId();
    if (routeId != null) {
      routeCountryDistanceRepository.deleteByRouteId(routeId);
    }
    routeRequest.setNbuBreakdownScenarioId(scenarioId);
    routeRequest.setNbuBreakdownAt(Instant.now());
    routeRequestRepository.save(routeRequest);
    return countryBreakdownService.getOrCalculate(routeRequest.getRoute());
  }

  private JsonNode buildBreakdownJson(
      FreightCostCalculationSummaryDto summary,
      String scenarioId,
      java.time.LocalDate calculationDate) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("scenarioId", scenarioId);
    root.put("calculationDate", calculationDate.toString());
    root.put("driverSalaryBasis", DriverSalaryBasis.PERCENT_OF_FINAL_FREIGHT.name());
    root.put("seasonUsed", summary.seasonUsed());
    root.put("preRouteEmptyKm", summary.preRouteEmptyKm());
    root.put("lengthFallbackUsed", summary.lengthFallbackUsed());
    root.put("lTotalKm", summary.lTotalKm());
    root.put("lEmptyKm", summary.lEmptyKm());
    root.put("lLoadedKm", summary.lLoadedKm());
    root.put("fuelCostUah", summary.fuelCostUah());
    root.put("perDiemUah", summary.perDiemUah());
    root.put("tollsUah", summary.tollsUah());
    root.put("directCostUah", summary.directCostUah());
    root.put("driverCostUah", summary.driverCostUah());
    root.put("costBeforeMarginUah", summary.costBeforeMarginUah());
    root.put("marginUah", summary.marginUah());
    root.put("totalUah", summary.totalUah());
    root.put("totalProposalAmount", summary.totalProposalAmount());
    root.put("proposalCurrency", summary.proposalCurrency());
    root.set("tollLines", objectMapper.valueToTree(summary.tollLines()));
    root.set(
        "nbuRates",
        objectMapper.valueToTree(
            Map.of(
                "rateDate", summary.nbuRateDate().toString(),
                "eurRatePerUnit", summary.eurRatePerUnit(),
                "usdRatePerUnit", summary.usdRatePerUnit(),
                "proposalRatePerUnit", summary.proposalRatePerUnit())));
    return root;
  }

  private Map<String, Object> scenarioSnapshot(FreightNumericScenario scenario) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", scenario.getId());
    map.put("name", scenario.getName());
    map.put("fuelPricePerLiter", scenario.getFuelPricePerLiter());
    map.put("driverSalaryPercentOfFreight", scenario.getDriverSalaryPercentOfFreight());
    map.put("marginType", scenario.getMarginType().name());
    map.put("marginPercent", scenario.getMarginPercent());
    map.put("marginFixedAmount", scenario.getMarginFixedAmount());
    map.put("proposalCurrency", scenario.getProposalCurrency());
    map.put("tollTariffSetId", scenario.getTollTariffSet().getId());
    return map;
  }

  private List<Map<String, Object>> tollSetSnapshot(String tollSetId) {
    return countryTollRuleRepository
        .findByTollTariffSet_IdAndActiveTrueOrderByCountryCodeAsc(tollSetId)
        .stream()
        .map(
            rule -> {
              Map<String, Object> row = new HashMap<>();
              row.put("countryCode", rule.getCountryCode());
              row.put("tollType", rule.getTollType().name());
              row.put("rate", rule.getRate());
              row.put("fixedDays", rule.getFixedDays());
              return row;
            })
        .toList();
  }

  private FreightCostCalculationDto toDto(FreightCostCalculation calculation) {
    JsonNode breakdown;
    try {
      breakdown = objectMapper.readTree(calculation.getBreakdownJson());
    } catch (Exception ex) {
      breakdown = objectMapper.createObjectNode();
    }
    return new FreightCostCalculationDto(
        calculation.getId(),
        calculation.getRouteRequest().getId(),
        calculation.getScenario().getId(),
        calculation.getScenario().getName(),
        calculation.getCalculationDate(),
        calculation.getSeasonUsed(),
        calculation.getLTotalKm(),
        calculation.getLEmptyKm(),
        calculation.getLLoadedKm(),
        calculation.getDirectCostUah(),
        calculation.getDriverCostUah(),
        calculation.getCostBeforeMarginUah(),
        calculation.getMarginUah(),
        calculation.getTotalUah(),
        calculation.getTotalProposalAmount(),
        calculation.getProposalCurrency(),
        breakdown,
        calculation.getCalculationSummary(),
        calculation.getCreatedAt() == null ? null : calculation.getCreatedAt().toString());
  }

  private RouteRequest loadRouteRequest(Long requestId) {
    return routeRequestRepository
        .findByIdAndEnterpriseId(
            Objects.requireNonNull(requestId, "requestId"), currentEnterprise.requireId())
        .orElseThrow(() -> ApiException.notFound("Route request not found"));
  }

  private User loadUser(String userId) {
    return userRepository
        .findById(Objects.requireNonNull(userId, "userId"))
        .orElseThrow(() -> ApiException.notFound("User not found"));
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Failed to serialize JSON snapshot");
    }
  }

  private FreightRouteLengthService.StartPoint resolveStartPoint(
      CostPreviewRequest.StartPointRequest startPoint) {
    if (startPoint == null) {
      return null;
    }
    if (startPoint.lat() == null || startPoint.lng() == null) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR",
          "startPoint.lat та startPoint.lng обов'язкові, якщо startPoint передано");
    }
    return new FreightRouteLengthService.StartPoint(
        startPoint.lat(), startPoint.lng(), startPoint.address());
  }
}
