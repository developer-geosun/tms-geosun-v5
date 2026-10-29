package com.geosun.tms.routes.service;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import com.geosun.tms.freight.cost.domain.FreightCostCalculation;
import com.geosun.tms.freight.cost.repository.FreightCostCalculationRepository;
import com.geosun.tms.routes.domain.FreightQuote;
import com.geosun.tms.routes.domain.QuoteIdempotencyKey;
import com.geosun.tms.routes.domain.RouteRequest;
import com.geosun.tms.routes.domain.RouteRequestStatusHistory;
import com.geosun.tms.routes.dto.QuoteStatus;
import com.geosun.tms.routes.dto.RouteRequestStatus;
import com.geosun.tms.routes.dto.request.CreateQuoteRequest;
import com.geosun.tms.routes.dto.response.QuoteDto;
import com.geosun.tms.routes.mail.QuoteProposalMailSender;
import com.geosun.tms.routes.repository.FreightQuoteRepository;
import com.geosun.tms.routes.repository.QuoteIdempotencyKeyRepository;
import com.geosun.tms.routes.repository.RouteRequestRepository;
import com.geosun.tms.routes.repository.RouteRequestStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FreightQuoteService {
  private static final String OP_CREATE = "CREATE_DRAFT";
  private static final String OP_SEND = "SEND";

  private final RouteRequestRepository routeRequestRepository;
  private final FreightQuoteRepository freightQuoteRepository;
  private final QuoteIdempotencyKeyRepository quoteIdempotencyKeyRepository;
  private final RouteRequestStatusHistoryRepository routeRequestStatusHistoryRepository;
  private final UserRepository userRepository;
  private final FreightCostCalculationRepository freightCostCalculationRepository;
  private final QuoteProposalMailSender quoteProposalMailSender;
  private final CurrentEnterprise currentEnterprise;

  public FreightQuoteService(
      RouteRequestRepository routeRequestRepository,
      FreightQuoteRepository freightQuoteRepository,
      QuoteIdempotencyKeyRepository quoteIdempotencyKeyRepository,
      RouteRequestStatusHistoryRepository routeRequestStatusHistoryRepository,
      UserRepository userRepository,
      FreightCostCalculationRepository freightCostCalculationRepository,
      QuoteProposalMailSender quoteProposalMailSender,
      CurrentEnterprise currentEnterprise) {
    this.routeRequestRepository = routeRequestRepository;
    this.freightQuoteRepository = freightQuoteRepository;
    this.quoteIdempotencyKeyRepository = quoteIdempotencyKeyRepository;
    this.routeRequestStatusHistoryRepository = routeRequestStatusHistoryRepository;
    this.userRepository = userRepository;
    this.freightCostCalculationRepository = freightCostCalculationRepository;
    this.quoteProposalMailSender = quoteProposalMailSender;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional
  public QuoteDto createDraftQuote(
      @NonNull Long requestId,
      @NonNull String adminUserId,
      @NonNull String idempotencyKey,
      @NonNull CreateQuoteRequest request) {
    String key = requireIdempotencyKey(idempotencyKey);
    QuoteIdempotencyKey existing = loadIdempotency(OP_CREATE, key, adminUserId);
    if (existing != null && existing.getQuote() != null) {
      return toDto(existing.getQuote());
    }

    String enterpriseId = currentEnterprise.requireId();
    User adminUser =
        userRepository
            .findById(adminUserId)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    RouteRequest routeRequest =
        routeRequestRepository
            .findByIdAndEnterpriseId(requestId, enterpriseId)
            .orElseThrow(() -> ApiException.notFound("Route request not found"));

    ResolvedQuoteFields fields = resolveQuoteFields(requestId, request);

    FreightQuote quote = new FreightQuote();
    quote.setEnterpriseId(enterpriseId);
    quote.setRequest(routeRequest);
    quote.setAdminUser(adminUser);
    quote.setCurrency(fields.currency());
    quote.setTotalAmount(fields.totalAmount());
    quote.setTransitDaysMin(request.transitDaysMin());
    quote.setTransitDaysMax(request.transitDaysMax());
    quote.setValidUntil(parseDateOrNull(request.validUntil()));
    quote.setPublicNote(request.publicNote());
    quote.setInternalNote(fields.internalNote());
    if (fields.costCalculation() != null) {
      quote.setFreightCostCalculation(fields.costCalculation());
    }
    quote.setStatus(QuoteStatus.DRAFT);
    FreightQuote saved = freightQuoteRepository.save(quote);

    if (routeRequest.getStatus() == RouteRequestStatus.NEW) {
      appendRequestStatusHistory(
          routeRequest,
          routeRequest.getStatus(),
          RouteRequestStatus.IN_REVIEW,
          adminUser,
          "Draft created");
      routeRequest.setStatus(RouteRequestStatus.IN_REVIEW);
    }

    persistIdempotency(OP_CREATE, key, adminUser, routeRequest, saved);
    return toDto(saved);
  }

  @Transactional
  public QuoteDto sendQuote(
      @NonNull String quoteId, @NonNull String adminUserId, @NonNull String idempotencyKey) {
    return sendQuote(quoteId, adminUserId, idempotencyKey, null);
  }

  @Transactional
  public QuoteDto sendQuote(
      @NonNull String quoteId,
      @NonNull String adminUserId,
      @NonNull String idempotencyKey,
      String messageBody) {
    String normalizedQuoteId = requireQuoteId(quoteId);
    String key = requireIdempotencyKey(idempotencyKey);
    QuoteIdempotencyKey existing = loadIdempotency(OP_SEND, key, adminUserId);
    if (existing != null && existing.getQuote() != null) {
      return toDto(existing.getQuote());
    }

    User adminUser =
        userRepository
            .findById(adminUserId)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    if (normalizedQuoteId == null) {
      throw new IllegalStateException("quoteId must not be null");
    }
    FreightQuote quote =
        freightQuoteRepository
            .findByIdAndEnterpriseId(normalizedQuoteId, currentEnterprise.requireId())
            .orElseThrow(() -> ApiException.notFound("Quote not found"));
    if (quote.getStatus() == QuoteStatus.SENT) {
      persistIdempotency(OP_SEND, key, adminUser, quote.getRequest(), quote);
      return toDto(quote);
    }
    if (quote.getStatus() != QuoteStatus.DRAFT) {
      throw ApiException.conflict("Only draft quote can be sent");
    }

    RouteRequest routeRequest = quote.getRequest();
    String recipientEmail =
        routeRequest.getUser() == null ? null : routeRequest.getUser().getEmail();
    if (!StringUtils.hasText(recipientEmail)) {
      throw ApiException.unprocessableEntity(
          "REQUESTER_EMAIL_MISSING", "У заявки відсутній email отримувача");
    }

    String body =
        StringUtils.hasText(messageBody) ? messageBody.trim() : defaultProposalBody(quote);
    try {
      quoteProposalMailSender.sendProposalEmail(recipientEmail, body);
    } catch (MailException ex) {
      throw ApiException.serviceUnavailable(
          "EMAIL_SEND_FAILED", "Не вдалося надіслати лист з пропозицією");
    }

    if (StringUtils.hasText(messageBody)) {
      quote.setPublicNote(messageBody.trim());
    }

    List<FreightQuote> sentQuotes =
        freightQuoteRepository.findByRequest_IdAndStatus(routeRequest.getId(), QuoteStatus.SENT);
    for (FreightQuote sent : sentQuotes) {
      sent.setStatus(QuoteStatus.SUPERSEDED);
    }

    quote.setStatus(QuoteStatus.SENT);
    quote.setSentAt(Instant.now());
    freightQuoteRepository.save(quote);

    RouteRequestStatus fromStatus = routeRequest.getStatus();
    routeRequest.setStatus(RouteRequestStatus.QUOTED);
    appendRequestStatusHistory(
        routeRequest, fromStatus, RouteRequestStatus.QUOTED, adminUser, "Quote sent");

    persistIdempotency(OP_SEND, key, adminUser, routeRequest, quote);
    return toDto(quote);
  }

  private static String defaultProposalBody(FreightQuote quote) {
    return "Пропозиція фрахту: " + quote.getTotalAmount() + " " + quote.getCurrency();
  }

  @Transactional(readOnly = true)
  public List<QuoteDto> getQuotesForRequest(@NonNull Long requestId) {
    routeRequestRepository
        .findByIdAndEnterpriseId(requestId, currentEnterprise.requireId())
        .orElseThrow(() -> ApiException.notFound("Route request not found"));
    return freightQuoteRepository.findByRequest_IdOrderByCreatedAtDesc(requestId).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public QuoteDto getCurrentQuoteForRequest(@NonNull Long requestId) {
    return freightQuoteRepository
        .findFirstByRequest_IdAndStatusInOrderByCreatedAtDesc(
            requestId, List.of(QuoteStatus.SENT, QuoteStatus.DRAFT))
        .map(this::toDto)
        .orElse(null);
  }

  private void appendRequestStatusHistory(
      RouteRequest request,
      RouteRequestStatus from,
      RouteRequestStatus to,
      User actor,
      String note) {
    RouteRequestStatusHistory history = new RouteRequestStatusHistory();
    history.setRequest(request);
    history.setFromStatus(from);
    history.setToStatus(to);
    history.setChangedBy(actor);
    history.setNote(note);
    routeRequestStatusHistoryRepository.save(history);
  }

  private QuoteIdempotencyKey loadIdempotency(String operation, String key, String actorUserId) {
    return quoteIdempotencyKeyRepository
        .findByOperationTypeAndIdempotencyKeyAndActorUserId(operation, key, actorUserId)
        .orElse(null);
  }

  private void persistIdempotency(
      String operation, String key, User actor, RouteRequest request, FreightQuote quote) {
    QuoteIdempotencyKey existing = loadIdempotency(operation, key, actor.getId());
    if (existing != null) {
      return;
    }
    QuoteIdempotencyKey entry = new QuoteIdempotencyKey();
    entry.setOperationType(operation);
    entry.setIdempotencyKey(key);
    entry.setActorUser(actor);
    entry.setRequest(request);
    entry.setQuote(quote);
    quoteIdempotencyKeyRepository.save(entry);
  }

  private static String requireIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      throw ApiException.badRequest(
          "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }
    return idempotencyKey.trim();
  }

  private static String requireQuoteId(String quoteId) {
    if (!StringUtils.hasText(quoteId)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Quote id is required");
    }
    return quoteId.trim();
  }

  private static LocalDate parseDateOrNull(String rawDate) {
    if (!StringUtils.hasText(rawDate)) {
      return null;
    }
    try {
      return LocalDate.parse(rawDate);
    } catch (DateTimeParseException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid validUntil format");
    }
  }

  private ResolvedQuoteFields resolveQuoteFields(Long requestId, CreateQuoteRequest request) {
    FreightCostCalculation calculation = null;
    if (StringUtils.hasText(request.fromCostCalculationId())) {
      calculation =
          freightCostCalculationRepository
              .findByIdAndEnterpriseIdAndRouteRequest_Id(
                  request.fromCostCalculationId().trim(), currentEnterprise.requireId(), requestId)
              .orElseThrow(
                  () -> ApiException.notFound("Cost calculation not found for this request"));
    }

    String currency =
        StringUtils.hasText(request.currency())
            ? request.currency().trim().toUpperCase()
            : calculation == null ? null : calculation.getProposalCurrency().toUpperCase();

    BigDecimal totalAmount = null;
    if (request.totalAmount() != null) {
      if (request.totalAmount() < 0.01) {
        throw ApiException.badRequest("VALIDATION_ERROR", "totalAmount must be at least 0.01");
      }
      totalAmount =
          BigDecimal.valueOf(request.totalAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
    } else if (calculation != null) {
      totalAmount = calculation.getTotalProposalAmount();
    }

    if (!StringUtils.hasText(currency) || totalAmount == null) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR",
          "currency and totalAmount are required without fromCostCalculationId");
    }

    String internalNote = request.internalNote();
    boolean copySummary =
        Boolean.TRUE.equals(request.copyCalculationSummaryToInternalNote())
            || (calculation != null
                && !StringUtils.hasText(internalNote)
                && request.copyCalculationSummaryToInternalNote() == null);
    if (copySummary && calculation != null) {
      internalNote = calculation.getCalculationSummary();
    }

    return new ResolvedQuoteFields(currency, totalAmount, internalNote, calculation);
  }

  private record ResolvedQuoteFields(
      String currency,
      BigDecimal totalAmount,
      String internalNote,
      FreightCostCalculation costCalculation) {}

  private QuoteDto toDto(FreightQuote quote) {
    String calculationId =
        quote.getFreightCostCalculation() == null
            ? null
            : quote.getFreightCostCalculation().getId();
    return new QuoteDto(
        quote.getId(),
        quote.getRequest().getId(),
        quote.getCurrency(),
        quote.getTotalAmount().doubleValue(),
        quote.getTransitDaysMin(),
        quote.getTransitDaysMax(),
        quote.getValidUntil() == null ? null : quote.getValidUntil().toString(),
        quote.getStatus(),
        quote.getPublicNote(),
        calculationId,
        quote.getCreatedAt() == null ? null : quote.getCreatedAt().toString(),
        quote.getSentAt() == null ? null : quote.getSentAt().toString());
  }
}
