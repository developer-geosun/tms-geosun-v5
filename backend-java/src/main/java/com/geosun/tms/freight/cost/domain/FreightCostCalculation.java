package com.geosun.tms.freight.cost.domain;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.routes.domain.RouteRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "freight_cost_calculations")
public class FreightCostCalculation {
  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "route_request_id", nullable = false)
  private RouteRequest routeRequest;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scenario_id", nullable = false)
  private FreightNumericScenario scenario;

  @Column(name = "calculation_date", nullable = false)
  private LocalDate calculationDate;

  @Column(name = "breakdown_json", nullable = false, columnDefinition = "json")
  private String breakdownJson;

  @Column(name = "calculation_summary", nullable = false, columnDefinition = "text")
  private String calculationSummary;

  @Column(name = "scenario_snapshot_json", nullable = false, columnDefinition = "json")
  private String scenarioSnapshotJson;

  @Column(name = "toll_tariff_set_snapshot_json", nullable = false, columnDefinition = "json")
  private String tollTariffSetSnapshotJson;

  @Column(name = "nbu_rates_snapshot_json", nullable = false, columnDefinition = "json")
  private String nbuRatesSnapshotJson;

  @Column(name = "season_used", nullable = false, length = 32)
  private String seasonUsed;

  @Column(name = "l_total_km", nullable = false, precision = 12, scale = 3)
  private BigDecimal lTotalKm;

  @Column(name = "l_empty_km", nullable = false, precision = 12, scale = 3)
  private BigDecimal lEmptyKm;

  @Column(name = "l_loaded_km", nullable = false, precision = 12, scale = 3)
  private BigDecimal lLoadedKm;

  @Column(name = "direct_cost_uah", nullable = false, precision = 14, scale = 2)
  private BigDecimal directCostUah;

  @Column(name = "driver_cost_uah", nullable = false, precision = 14, scale = 2)
  private BigDecimal driverCostUah;

  @Column(name = "cost_before_margin_uah", nullable = false, precision = 14, scale = 2)
  private BigDecimal costBeforeMarginUah;

  @Column(name = "margin_uah", nullable = false, precision = 14, scale = 2)
  private BigDecimal marginUah;

  @Column(name = "total_uah", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalUah;

  @Column(name = "total_proposal_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalProposalAmount;

  @Column(name = "proposal_currency", nullable = false, length = 3)
  private String proposalCurrency;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", nullable = false)
  private User createdBy;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }

  public String getId() {
    return id;
  }

  public RouteRequest getRouteRequest() {
    return routeRequest;
  }

  public void setRouteRequest(RouteRequest routeRequest) {
    this.routeRequest = routeRequest;
  }

  public FreightNumericScenario getScenario() {
    return scenario;
  }

  public void setScenario(FreightNumericScenario scenario) {
    this.scenario = scenario;
  }

  public LocalDate getCalculationDate() {
    return calculationDate;
  }

  public void setCalculationDate(LocalDate calculationDate) {
    this.calculationDate = calculationDate;
  }

  public String getBreakdownJson() {
    return breakdownJson;
  }

  public void setBreakdownJson(String breakdownJson) {
    this.breakdownJson = breakdownJson;
  }

  public String getCalculationSummary() {
    return calculationSummary;
  }

  public void setCalculationSummary(String calculationSummary) {
    this.calculationSummary = calculationSummary;
  }

  public String getScenarioSnapshotJson() {
    return scenarioSnapshotJson;
  }

  public void setScenarioSnapshotJson(String scenarioSnapshotJson) {
    this.scenarioSnapshotJson = scenarioSnapshotJson;
  }

  public String getTollTariffSetSnapshotJson() {
    return tollTariffSetSnapshotJson;
  }

  public void setTollTariffSetSnapshotJson(String tollTariffSetSnapshotJson) {
    this.tollTariffSetSnapshotJson = tollTariffSetSnapshotJson;
  }

  public String getNbuRatesSnapshotJson() {
    return nbuRatesSnapshotJson;
  }

  public void setNbuRatesSnapshotJson(String nbuRatesSnapshotJson) {
    this.nbuRatesSnapshotJson = nbuRatesSnapshotJson;
  }

  public String getSeasonUsed() {
    return seasonUsed;
  }

  public void setSeasonUsed(String seasonUsed) {
    this.seasonUsed = seasonUsed;
  }

  public BigDecimal getLTotalKm() {
    return lTotalKm;
  }

  public void setLTotalKm(BigDecimal lTotalKm) {
    this.lTotalKm = lTotalKm;
  }

  public BigDecimal getLEmptyKm() {
    return lEmptyKm;
  }

  public void setLEmptyKm(BigDecimal lEmptyKm) {
    this.lEmptyKm = lEmptyKm;
  }

  public BigDecimal getLLoadedKm() {
    return lLoadedKm;
  }

  public void setLLoadedKm(BigDecimal lLoadedKm) {
    this.lLoadedKm = lLoadedKm;
  }

  public BigDecimal getDirectCostUah() {
    return directCostUah;
  }

  public void setDirectCostUah(BigDecimal directCostUah) {
    this.directCostUah = directCostUah;
  }

  public BigDecimal getDriverCostUah() {
    return driverCostUah;
  }

  public void setDriverCostUah(BigDecimal driverCostUah) {
    this.driverCostUah = driverCostUah;
  }

  public BigDecimal getCostBeforeMarginUah() {
    return costBeforeMarginUah;
  }

  public void setCostBeforeMarginUah(BigDecimal costBeforeMarginUah) {
    this.costBeforeMarginUah = costBeforeMarginUah;
  }

  public BigDecimal getMarginUah() {
    return marginUah;
  }

  public void setMarginUah(BigDecimal marginUah) {
    this.marginUah = marginUah;
  }

  public BigDecimal getTotalUah() {
    return totalUah;
  }

  public void setTotalUah(BigDecimal totalUah) {
    this.totalUah = totalUah;
  }

  public BigDecimal getTotalProposalAmount() {
    return totalProposalAmount;
  }

  public void setTotalProposalAmount(BigDecimal totalProposalAmount) {
    this.totalProposalAmount = totalProposalAmount;
  }

  public String getProposalCurrency() {
    return proposalCurrency;
  }

  public void setProposalCurrency(String proposalCurrency) {
    this.proposalCurrency = proposalCurrency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
