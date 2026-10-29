package com.geosun.tms.freight.cost.service;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.freight.cost.domain.CountryTollRule;
import com.geosun.tms.freight.cost.domain.TollTariffSet;
import com.geosun.tms.freight.cost.domain.TollType;
import com.geosun.tms.freight.cost.dto.request.CreateCountryTollRuleRequest;
import com.geosun.tms.freight.cost.dto.request.CreateTollTariffSetRequest;
import com.geosun.tms.freight.cost.dto.request.UpdateCountryTollRuleRequest;
import com.geosun.tms.freight.cost.dto.request.UpdateTollTariffSetRequest;
import com.geosun.tms.freight.cost.dto.response.CountryTollRuleDto;
import com.geosun.tms.freight.cost.dto.response.TollTariffSetDto;
import com.geosun.tms.freight.cost.repository.CountryTollRuleRepository;
import com.geosun.tms.freight.cost.repository.FreightNumericScenarioRepository;
import com.geosun.tms.freight.cost.repository.TollTariffSetRepository;
import com.geosun.tms.reference.service.CountryReferenceService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TollTariffSetService {
  private final TollTariffSetRepository tollTariffSetRepository;
  private final CountryTollRuleRepository countryTollRuleRepository;
  private final FreightNumericScenarioRepository scenarioRepository;
  private final UserRepository userRepository;
  private final CurrentEnterprise currentEnterprise;

  public TollTariffSetService(
      TollTariffSetRepository tollTariffSetRepository,
      CountryTollRuleRepository countryTollRuleRepository,
      FreightNumericScenarioRepository scenarioRepository,
      UserRepository userRepository,
      CurrentEnterprise currentEnterprise) {
    this.tollTariffSetRepository = tollTariffSetRepository;
    this.countryTollRuleRepository = countryTollRuleRepository;
    this.scenarioRepository = scenarioRepository;
    this.userRepository = userRepository;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional(readOnly = true)
  public List<TollTariffSetDto> list(boolean activeOnly) {
    String enterpriseId = currentEnterprise.requireId();
    List<TollTariffSet> sets =
        activeOnly
            ? tollTariffSetRepository.findByEnterpriseIdAndActiveTrueOrderByNameAsc(enterpriseId)
            : tollTariffSetRepository.findByEnterpriseIdOrderByNameAsc(enterpriseId);
    return sets.stream().map(this::toSetDto).toList();
  }

  @Transactional(readOnly = true)
  public TollTariffSetDto getById(String id) {
    return toSetDto(loadSet(id));
  }

  @Transactional
  public TollTariffSetDto create(String userId, CreateTollTariffSetRequest request) {
    User user = loadUser(userId);
    validateUniqueActiveName(request.name(), null);
    TollTariffSet set = new TollTariffSet();
    set.setEnterpriseId(currentEnterprise.requireId());
    set.setName(request.name().trim());
    set.setDescription(request.description());
    set.setActive(request.isActive() == null || request.isActive());
    set.setCreatedBy(user);
    set.setUpdatedBy(user);
    return toSetDto(tollTariffSetRepository.save(set));
  }

  @Transactional
  public TollTariffSetDto update(String userId, String id, UpdateTollTariffSetRequest request) {
    User user = loadUser(userId);
    TollTariffSet set = loadSet(id);
    validateUniqueActiveName(request.name(), id);
    set.setName(request.name().trim());
    set.setDescription(request.description());
    set.setActive(request.isActive());
    set.setUpdatedBy(user);
    return toSetDto(tollTariffSetRepository.save(set));
  }

  @Transactional
  public void delete(String id) {
    TollTariffSet set = loadSet(id);
    if (scenarioRepository.existsByTollTariffSet_IdAndActiveTrue(id)) {
      throw ApiException.conflict(
          "TOLL_TARIFF_SET_IN_USE", "Cannot delete toll tariff set referenced by active scenarios");
    }
    if (scenarioRepository.existsByTollTariffSet_Id(id)) {
      set.setActive(false);
      tollTariffSetRepository.save(set);
      return;
    }
    if (set == null) {
      throw new NullPointerException("set");
    }
    tollTariffSetRepository.delete(set);
  }

  @Transactional(readOnly = true)
  public List<CountryTollRuleDto> listRules(String setId) {
    loadSet(setId);
    return countryTollRuleRepository.findByTollTariffSet_IdOrderByCountryCodeAsc(setId).stream()
        .map(rule -> toRuleDto(setId, rule))
        .toList();
  }

  @Transactional
  public CountryTollRuleDto createRule(
      String userId, String setId, CreateCountryTollRuleRequest request) {
    TollTariffSet set = loadSet(setId);
    String countryCode = CountryReferenceService.normalizeCountryCode(request.countryCode());
    if (countryTollRuleRepository
        .findByTollTariffSet_IdAndCountryCode(setId, countryCode)
        .isPresent()) {
      throw ApiException.conflict("COUNTRY_TOLL_RULE_EXISTS", "Rule for country already exists");
    }
    validateTollRule(request.tollType(), request.fixedDays());
    CountryTollRule rule = new CountryTollRule();
    rule.setTollTariffSet(set);
    rule.setCountryCode(countryCode);
    rule.setTollType(request.tollType());
    rule.setRate(request.rate());
    rule.setFixedDays(request.fixedDays());
    rule.setActive(request.isActive() == null || request.isActive());
    return toRuleDto(setId, countryTollRuleRepository.save(rule));
  }

  @Transactional
  public CountryTollRuleDto updateRule(
      String setId, String ruleId, UpdateCountryTollRuleRequest request) {
    loadSet(setId);
    CountryTollRule rule = loadRule(setId, ruleId);
    if (StringUtils.hasText(request.countryCode())) {
      String countryCode = CountryReferenceService.normalizeCountryCode(request.countryCode());
      if (countryTollRuleRepository.existsByTollTariffSet_IdAndCountryCodeAndIdNot(
          setId, countryCode, ruleId)) {
        throw ApiException.conflict("COUNTRY_TOLL_RULE_EXISTS", "Rule for country already exists");
      }
      rule.setCountryCode(countryCode);
    }
    validateTollRule(request.tollType(), request.fixedDays());
    rule.setTollType(request.tollType());
    rule.setRate(request.rate());
    rule.setFixedDays(request.fixedDays());
    rule.setActive(request.isActive());
    return toRuleDto(setId, countryTollRuleRepository.save(rule));
  }

  @Transactional
  public void deleteRule(String setId, String ruleId) {
    loadSet(setId);
    CountryTollRule rule = loadRule(setId, ruleId);
    if (rule == null) {
      throw new NullPointerException("rule");
    }
    countryTollRuleRepository.delete(rule);
  }

  public TollTariffSet loadSet(String id) {
    return tollTariffSetRepository
        .findByIdAndEnterpriseId(Objects.requireNonNull(id, "setId"), currentEnterprise.requireId())
        .orElseThrow(() -> ApiException.notFound("Toll tariff set not found"));
  }

  private CountryTollRule loadRule(String setId, String ruleId) {
    CountryTollRule rule =
        countryTollRuleRepository
            .findById(Objects.requireNonNull(ruleId, "ruleId"))
            .orElseThrow(() -> ApiException.notFound("Country toll rule not found"));
    if (!rule.getTollTariffSet().getId().equals(setId)) {
      throw ApiException.notFound("Country toll rule not found in set");
    }
    return rule;
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
            ? tollTariffSetRepository
                .findByEnterpriseIdAndNameIgnoreCaseAndActiveTrue(enterpriseId, name.trim())
                .isPresent()
            : tollTariffSetRepository.existsByEnterpriseIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
                enterpriseId, name.trim(), excludeId);
    if (exists) {
      throw ApiException.conflict(
          "TOLL_TARIFF_SET_NAME_CONFLICT", "Active toll tariff set name already exists");
    }
  }

  private static void validateTollRule(TollType tollType, Integer fixedDays) {
    if (tollType == TollType.EUR_PER_DAY && (fixedDays == null || fixedDays <= 0)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "fixedDays is required for EUR_PER_DAY");
    }
  }

  private TollTariffSetDto toSetDto(TollTariffSet set) {
    return new TollTariffSetDto(
        set.getId(),
        set.getName(),
        set.getDescription(),
        set.isActive(),
        set.getCreatedAt() == null ? null : set.getCreatedAt().toString(),
        set.getUpdatedAt() == null ? null : set.getUpdatedAt().toString());
  }

  private CountryTollRuleDto toRuleDto(String setId, CountryTollRule rule) {
    return new CountryTollRuleDto(
        rule.getId(),
        setId,
        rule.getCountryCode(),
        rule.getTollType(),
        rule.getRate(),
        rule.getFixedDays(),
        rule.isActive(),
        rule.getCreatedAt() == null ? null : rule.getCreatedAt().toString(),
        rule.getUpdatedAt() == null ? null : rule.getUpdatedAt().toString());
  }
}
