package com.geosun.tms.enterprise.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.reference.dto.request.UpdateCurrencyRequest;
import com.geosun.tms.reference.dto.response.CountryReferenceDto;
import com.geosun.tms.reference.dto.response.CurrencyDto;
import com.geosun.tms.reference.dto.response.NbuRatesSnapshotDto;
import com.geosun.tms.reference.dto.response.SyncNbuRatesResponse;
import com.geosun.tms.reference.service.CountryReferenceService;
import com.geosun.tms.reference.service.CurrencyService;
import com.geosun.tms.reference.service.NbuExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Спільні довідники (валюти, курси НБУ, країни) для оператора платформи. Запис валют і синхронізація
 * НБУ доступні лише тут; співробітники підприємства читають довідники через /api/v1/admin/**.
 */
@Tag(name = "Platform Reference")
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_OPERATOR')")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class PlatformReferenceController {

  private final CurrencyService currencyService;
  private final NbuExchangeRateService nbuExchangeRateService;
  private final CountryReferenceService countryReferenceService;

  public PlatformReferenceController(
      CurrencyService currencyService,
      NbuExchangeRateService nbuExchangeRateService,
      CountryReferenceService countryReferenceService) {
    this.currencyService = currencyService;
    this.nbuExchangeRateService = nbuExchangeRateService;
    this.countryReferenceService = countryReferenceService;
  }

  @Operation(summary = "List currencies")
  @GetMapping("/currencies")
  public List<CurrencyDto> listCurrencies(
      @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
    return currencyService.list(activeOnly);
  }

  @Operation(summary = "Update currency active flag and display order")
  @PatchMapping("/currencies/{code}")
  public CurrencyDto updateCurrency(
      @PathVariable("code") @NonNull String code,
      @Valid @RequestBody @NonNull UpdateCurrencyRequest request) {
    return currencyService.update(code, request);
  }

  @Operation(summary = "Sync NBU exchange rates for active currencies")
  @PostMapping("/currencies/nbu-rates/sync")
  public ResponseEntity<SyncNbuRatesResponse> syncNbuRates() {
    return ResponseEntity.status(HttpStatus.OK).body(nbuExchangeRateService.syncActiveCurrencies());
  }

  @Operation(summary = "Get stored NBU rates snapshot")
  @GetMapping("/currencies/nbu-rates")
  public NbuRatesSnapshotDto getNbuRates(
      @RequestParam(name = "rateDate", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate rateDate) {
    if (rateDate == null) {
      return nbuExchangeRateService.getLatestRates();
    }
    return nbuExchangeRateService.getRatesForDate(rateDate);
  }

  @Operation(summary = "List countries reference")
  @GetMapping("/country-reference")
  public List<CountryReferenceDto> listCountries(
      @RequestParam(name = "search", required = false) String search) {
    return countryReferenceService.list(search);
  }

  @Operation(summary = "Get country by ISO alpha-2 code")
  @GetMapping("/country-reference/{codeAlpha2}")
  public CountryReferenceDto getCountry(@PathVariable("codeAlpha2") @NonNull String codeAlpha2) {
    return countryReferenceService.getByCodeAlpha2(codeAlpha2);
  }
}
