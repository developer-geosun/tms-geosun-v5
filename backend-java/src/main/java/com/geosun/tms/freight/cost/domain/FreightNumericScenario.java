package com.geosun.tms.freight.cost.domain;

import com.geosun.tms.auth.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "freight_numeric_scenarios")
public class FreightNumericScenario {
  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "fuel_consumption_empty_l_per_100km", nullable = false, precision = 8, scale = 2)
  private BigDecimal fuelConsumptionEmptyLPer100km;

  @Column(
      name = "fuel_consumption_loaded_non_winter_l_per_100km",
      nullable = false,
      precision = 8,
      scale = 2)
  private BigDecimal fuelConsumptionLoadedNonWinterLPer100km;

  @Column(
      name = "fuel_consumption_loaded_winter_l_per_100km",
      nullable = false,
      precision = 8,
      scale = 2)
  private BigDecimal fuelConsumptionLoadedWinterLPer100km;

  @Enumerated(EnumType.STRING)
  @Column(name = "season_mode", nullable = false, length = 32)
  private SeasonMode seasonMode;

  @Column(name = "fuel_price_per_liter", nullable = false, precision = 12, scale = 4)
  private BigDecimal fuelPricePerLiter;

  @Column(name = "driver_salary_percent_of_freight", nullable = false, precision = 8, scale = 4)
  private BigDecimal driverSalaryPercentOfFreight;

  @Column(name = "per_diem_amount_per_day", nullable = false, precision = 12, scale = 4)
  private BigDecimal perDiemAmountPerDay;

  @Column(name = "per_diem_route_divisor_km", nullable = false)
  private int perDiemRouteDivisorKm = 600;

  @Column(name = "per_diem_fixed_extra_days", nullable = false)
  private int perDiemFixedExtraDays = 2;

  @Enumerated(EnumType.STRING)
  @Column(name = "margin_type", nullable = false, length = 64)
  private MarginType marginType;

  @Column(name = "margin_percent", precision = 8, scale = 4)
  private BigDecimal marginPercent;

  @Column(name = "margin_fixed_amount", precision = 14, scale = 2)
  private BigDecimal marginFixedAmount;

  @Column(name = "proposal_currency", nullable = false, length = 3)
  private String proposalCurrency;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "toll_tariff_set_id", nullable = false)
  private TollTariffSet tollTariffSet;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", nullable = false)
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "updated_by_user_id", nullable = false)
  private User updatedBy;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public BigDecimal getFuelConsumptionEmptyLPer100km() {
    return fuelConsumptionEmptyLPer100km;
  }

  public void setFuelConsumptionEmptyLPer100km(BigDecimal fuelConsumptionEmptyLPer100km) {
    this.fuelConsumptionEmptyLPer100km = fuelConsumptionEmptyLPer100km;
  }

  public BigDecimal getFuelConsumptionLoadedNonWinterLPer100km() {
    return fuelConsumptionLoadedNonWinterLPer100km;
  }

  public void setFuelConsumptionLoadedNonWinterLPer100km(
      BigDecimal fuelConsumptionLoadedNonWinterLPer100km) {
    this.fuelConsumptionLoadedNonWinterLPer100km = fuelConsumptionLoadedNonWinterLPer100km;
  }

  public BigDecimal getFuelConsumptionLoadedWinterLPer100km() {
    return fuelConsumptionLoadedWinterLPer100km;
  }

  public void setFuelConsumptionLoadedWinterLPer100km(
      BigDecimal fuelConsumptionLoadedWinterLPer100km) {
    this.fuelConsumptionLoadedWinterLPer100km = fuelConsumptionLoadedWinterLPer100km;
  }

  public SeasonMode getSeasonMode() {
    return seasonMode;
  }

  public void setSeasonMode(SeasonMode seasonMode) {
    this.seasonMode = seasonMode;
  }

  public BigDecimal getFuelPricePerLiter() {
    return fuelPricePerLiter;
  }

  public void setFuelPricePerLiter(BigDecimal fuelPricePerLiter) {
    this.fuelPricePerLiter = fuelPricePerLiter;
  }

  public BigDecimal getDriverSalaryPercentOfFreight() {
    return driverSalaryPercentOfFreight;
  }

  public void setDriverSalaryPercentOfFreight(BigDecimal driverSalaryPercentOfFreight) {
    this.driverSalaryPercentOfFreight = driverSalaryPercentOfFreight;
  }

  public BigDecimal getPerDiemAmountPerDay() {
    return perDiemAmountPerDay;
  }

  public void setPerDiemAmountPerDay(BigDecimal perDiemAmountPerDay) {
    this.perDiemAmountPerDay = perDiemAmountPerDay;
  }

  public int getPerDiemRouteDivisorKm() {
    return perDiemRouteDivisorKm;
  }

  public void setPerDiemRouteDivisorKm(int perDiemRouteDivisorKm) {
    this.perDiemRouteDivisorKm = perDiemRouteDivisorKm;
  }

  public int getPerDiemFixedExtraDays() {
    return perDiemFixedExtraDays;
  }

  public void setPerDiemFixedExtraDays(int perDiemFixedExtraDays) {
    this.perDiemFixedExtraDays = perDiemFixedExtraDays;
  }

  public MarginType getMarginType() {
    return marginType;
  }

  public void setMarginType(MarginType marginType) {
    this.marginType = marginType;
  }

  public BigDecimal getMarginPercent() {
    return marginPercent;
  }

  public void setMarginPercent(BigDecimal marginPercent) {
    this.marginPercent = marginPercent;
  }

  public BigDecimal getMarginFixedAmount() {
    return marginFixedAmount;
  }

  public void setMarginFixedAmount(BigDecimal marginFixedAmount) {
    this.marginFixedAmount = marginFixedAmount;
  }

  public String getProposalCurrency() {
    return proposalCurrency;
  }

  public void setProposalCurrency(String proposalCurrency) {
    this.proposalCurrency = proposalCurrency;
  }

  public TollTariffSet getTollTariffSet() {
    return tollTariffSet;
  }

  public void setTollTariffSet(TollTariffSet tollTariffSet) {
    this.tollTariffSet = tollTariffSet;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public User getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(User updatedBy) {
    this.updatedBy = updatedBy;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
