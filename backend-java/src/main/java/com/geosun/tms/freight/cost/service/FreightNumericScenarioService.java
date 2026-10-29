package com.geosun.tms.freight.cost.service;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.freight.cost.domain.FreightNumericScenario;
import com.geosun.tms.freight.cost.domain.MarginType;
import com.geosun.tms.freight.cost.domain.TollTariffSet;
import com.geosun.tms.freight.cost.dto.request.CreateFreightNumericScenarioRequest;
import com.geosun.tms.freight.cost.dto.request.UpdateFreightNumericScenarioRequest;
import com.geosun.tms.freight.cost.dto.response.FreightNumericScenarioDto;
import com.geosun.tms.freight.cost.repository.FreightCostCalculationRepository;
import com.geosun.tms.freight.cost.repository.FreightNumericScenarioRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FreightNumericScenarioService {
  private final FreightNumericScenarioRepository scenarioRepository;
  private final FreightCostCalculationRepository calculationRepository;
  private final TollTariffSetService tollTariffSetService;
  private final UserRepository userRepository;
  private final CurrentEnterprise currentEnterprise;

  public FreightNumericScenarioService(
      FreightNumericScenarioRepository scenarioRepository,
      FreightCostCalculationRepository calculationRepository,
      TollTariffSetService tollTariffSetService,
      UserRepository userRepository,
      CurrentEnterprise currentEnterprise) {
    this.scenarioRepository = scenarioRepository;
    this.calculationRepository = calculationRepository;
    this.tollTariffSetService = tollTariffSetService;
    this.userRepository = userRepository;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional(readOnly = true)
  public List<FreightNumericScenarioDto> list(boolean activeOnly) {
    String enterpriseId = currentEnterprise.requireId();
    List<FreightNumericScenario> scenarios =
        activeOnly
            ? scenarioRepository.findByEnterpriseIdAndActiveTrueOrderByNameAsc(enterpriseId)
            : scenarioRepository.findByEnterpriseIdOrderByNameAsc(enterpriseId);
    return scenarios.stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public FreightNumericScenarioDto getById(String id) {
    return toDto(loadScenario(id));
  }

  @Transactional
  public FreightNumericScenarioDto create(
      String userId, CreateFreightNumericScenarioRequest request) {
    User user = loadUser(userId);
    validateUniqueActiveName(request.name(), null);
    validateMargin(request.marginType(), request.marginPercent(), request.marginFixedAmount());
    TollTariffSet tollSet = tollTariffSetService.loadSet(request.tollTariffSetId());
    FreightNumericScenario scenario = new FreightNumericScenario();
    scenario.setEnterpriseId(currentEnterprise.requireId());
    applyCreateOrUpdate(scenario, request, tollSet);
    scenario.setName(request.name().trim());
    scenario.setDescription(request.description());
    scenario.setActive(request.isActive() == null || request.isActive());
    scenario.setCreatedBy(user);
    scenario.setUpdatedBy(user);
    return toDto(scenarioRepository.save(scenario));
  }

  @Transactional
  public FreightNumericScenarioDto update(
      String userId, String id, UpdateFreightNumericScenarioRequest request) {
    User user = loadUser(userId);
    FreightNumericScenario scenario = loadScenario(id);
    validateUniqueActiveName(request.name(), id);
    validateMargin(request.marginType(), request.marginPercent(), request.marginFixedAmount());
    TollTariffSet tollSet = tollTariffSetService.loadSet(request.tollTariffSetId());
    scenario.setName(request.name().trim());
    scenario.setDescription(request.description());
    scenario.setActive(request.isActive());
    applyUpdate(scenario, request, tollSet);
    scenario.setUpdatedBy(user);
    return toDto(scenarioRepository.save(scenario));
  }

  @Transactional
  public void delete(String id) {
    FreightNumericScenario scenario = loadScenario(id);
    if (calculationRepository.existsByScenario_Id(id)) {
      scenario.setActive(false);
      scenarioRepository.save(scenario);
      return;
    }
    scenarioRepository.delete(scenario);
  }

  @NonNull
  public FreightNumericScenario loadScenario(String id) {
    FreightNumericScenario scenario =
        scenarioRepository
            .findByIdAndEnterpriseId(
                Objects.requireNonNull(id, "scenarioId"), currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Numeric scenario not found"));
    return Objects.requireNonNull(scenario);
  }

  private User loadUser(String userId) {
    return userRepository
        .findById(Objects.requireNonNull(userId, "userId"))
        .orElseThrow(() -> ApiException.notFound("User not found"));
  }

  private void validateUniqueActiveName(String name, String excludeId) {
    if (!StringUtils.hasText(name)) {
      return;
    }
    String enterpriseId = currentEnterprise.requireId();
    boolean exists =
        excludeId == null
            ? scenarioRepository
                .findByEnterpriseIdAndNameIgnoreCaseAndActiveTrue(enterpriseId, name.trim())
                .isPresent()
            : scenarioRepository.existsByEnterpriseIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
                enterpriseId, name.trim(), excludeId);
    if (exists) {
      throw ApiException.conflict(
          "SCENARIO_NAME_CONFLICT", "Active numeric scenario name already exists");
    }
  }

  private static void validateMargin(
      MarginType marginType, java.math.BigDecimal marginPercent, java.math.BigDecimal marginFixed) {
    if (marginType == MarginType.PERCENT_OF_COST_BEFORE_MARGIN && marginPercent == null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "marginPercent is required");
    }
    if (marginType == MarginType.FIXED_PER_TRIP && marginFixed == null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "marginFixedAmount is required");
    }
  }

  private void applyCreateOrUpdate(
      FreightNumericScenario scenario,
      CreateFreightNumericScenarioRequest request,
      TollTariffSet tollSet) {
    scenario.setFuelConsumptionEmptyLPer100km(request.fuelConsumptionEmptyLPer100km());
    scenario.setFuelConsumptionLoadedNonWinterLPer100km(
        request.fuelConsumptionLoadedNonWinterLPer100km());
    scenario.setFuelConsumptionLoadedWinterLPer100km(
        request.fuelConsumptionLoadedWinterLPer100km());
    scenario.setSeasonMode(request.seasonMode());
    scenario.setFuelPricePerLiter(request.fuelPricePerLiter());
    scenario.setDriverSalaryPercentOfFreight(request.driverSalaryPercentOfFreight());
    scenario.setPerDiemAmountPerDay(request.perDiemAmountPerDay());
    scenario.setPerDiemRouteDivisorKm(request.perDiemRouteDivisorKm());
    scenario.setPerDiemFixedExtraDays(request.perDiemFixedExtraDays());
    scenario.setMarginType(request.marginType());
    scenario.setMarginPercent(request.marginPercent());
    scenario.setMarginFixedAmount(request.marginFixedAmount());
    scenario.setProposalCurrency(request.proposalCurrency().toUpperCase());
    scenario.setTollTariffSet(tollSet);
  }

  private void applyUpdate(
      FreightNumericScenario scenario,
      UpdateFreightNumericScenarioRequest request,
      TollTariffSet tollSet) {
    scenario.setFuelConsumptionEmptyLPer100km(request.fuelConsumptionEmptyLPer100km());
    scenario.setFuelConsumptionLoadedNonWinterLPer100km(
        request.fuelConsumptionLoadedNonWinterLPer100km());
    scenario.setFuelConsumptionLoadedWinterLPer100km(
        request.fuelConsumptionLoadedWinterLPer100km());
    scenario.setSeasonMode(request.seasonMode());
    scenario.setFuelPricePerLiter(request.fuelPricePerLiter());
    scenario.setDriverSalaryPercentOfFreight(request.driverSalaryPercentOfFreight());
    scenario.setPerDiemAmountPerDay(request.perDiemAmountPerDay());
    scenario.setPerDiemRouteDivisorKm(request.perDiemRouteDivisorKm());
    scenario.setPerDiemFixedExtraDays(request.perDiemFixedExtraDays());
    scenario.setMarginType(request.marginType());
    scenario.setMarginPercent(request.marginPercent());
    scenario.setMarginFixedAmount(request.marginFixedAmount());
    scenario.setProposalCurrency(request.proposalCurrency().toUpperCase());
    scenario.setTollTariffSet(tollSet);
  }

  private FreightNumericScenarioDto toDto(FreightNumericScenario scenario) {
    return new FreightNumericScenarioDto(
        scenario.getId(),
        scenario.getName(),
        scenario.getDescription(),
        scenario.isActive(),
        scenario.getFuelConsumptionEmptyLPer100km(),
        scenario.getFuelConsumptionLoadedNonWinterLPer100km(),
        scenario.getFuelConsumptionLoadedWinterLPer100km(),
        scenario.getSeasonMode(),
        scenario.getFuelPricePerLiter(),
        scenario.getDriverSalaryPercentOfFreight(),
        scenario.getPerDiemAmountPerDay(),
        scenario.getPerDiemRouteDivisorKm(),
        scenario.getPerDiemFixedExtraDays(),
        scenario.getMarginType(),
        scenario.getMarginPercent(),
        scenario.getMarginFixedAmount(),
        scenario.getProposalCurrency(),
        scenario.getTollTariffSet().getId(),
        scenario.getTollTariffSet().getName(),
        scenario.getCreatedAt() == null ? null : scenario.getCreatedAt().toString(),
        scenario.getUpdatedAt() == null ? null : scenario.getUpdatedAt().toString());
  }
}
