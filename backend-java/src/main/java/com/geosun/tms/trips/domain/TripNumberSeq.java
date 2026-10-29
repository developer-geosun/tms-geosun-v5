package com.geosun.tms.trips.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Лічильник номерів рейсу: власний на пару (підприємство, рік). */
@Entity
@Table(name = "trip_number_seq")
@IdClass(TripNumberSeqId.class)
public class TripNumberSeq {

  @Id
  @Column(name = "enterprise_id", nullable = false, length = 36)
  private String enterpriseId;

  @Id
  @Column(name = "seq_year", nullable = false)
  private int year;

  @Column(name = "last_seq", nullable = false)
  private int lastValue;

  public int getYear() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }

  public int getLastValue() {
    return lastValue;
  }

  public void setLastValue(int lastValue) {
    this.lastValue = lastValue;
  }
}
