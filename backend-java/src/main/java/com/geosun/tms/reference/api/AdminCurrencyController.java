package com.geosun.tms.reference.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.reference.dto.response.CurrencyDto;
import com.geosun.tms.reference.dto.response.NbuRatesSnapshotDto;
import com.geosun.tms.reference.service.CurrencyService;
import com.geosun.tms.reference.service.NbuExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Currencies")
@RestController
@RequestMapping(ReferenceApiPaths.ADMIN_CURRENCIES_BASE)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminCurrencyController {
  private final CurrencyService currencyService;
  private final NbuExchangeRateService nbuExchangeRateService;

  public AdminCurrencyController(
      CurrencyService currencyService, NbuExchangeRateService nbuExchangeRateService) {
    this.currencyService = currencyService;
    this.nbuExchangeRateService = nbuExchangeRateService;
  }

  @Operation(summary = "List currencies")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @GetMapping
  public List<CurrencyDto> list(
      @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
    return currencyService.list(activeOnly);
  }

  @Operation(summary = "Get latest stored NBU rates snapshot")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @GetMapping("/nbu-rates")
  public NbuRatesSnapshotDto getNbuRates(
      @RequestParam(name = "rateDate", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate rateDate) {
    if (rateDate == null) {
      return nbuExchangeRateService.getLatestRates();
    }
    return nbuExchangeRateService.getRatesForDate(rateDate);
  }
}
