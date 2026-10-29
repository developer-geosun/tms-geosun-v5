package com.geosun.tms.trips.domain;

import java.io.Serializable;
import java.util.Objects;

/** Складений ключ лічильника номерів рейсу: (підприємство, рік). */
public class TripNumberSeqId implements Serializable {

  private static final long serialVersionUID = 1L;

  private String enterpriseId;
  private int year;

  public TripNumberSeqId() {}

  public TripNumberSeqId(String enterpriseId, int year) {
    this.enterpriseId = enterpriseId;
    this.year = year;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public int getYear() {
    return year;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TripNumberSeqId other)) {
      return false;
    }
    return year == other.year && Objects.equals(enterpriseId, other.enterpriseId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enterpriseId, year);
  }
}
