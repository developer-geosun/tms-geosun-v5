package com.geosun.tms.chatbot.service;

import com.geosun.tms.auth.domain.profile.PhoneE164Normalizer;
import com.geosun.tms.auth.domain.profile.UserContactPhone;
import com.geosun.tms.auth.repository.UserContactPhoneRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.chatbot.adapter.ChatbotChannelAdapter;
import com.geosun.tms.chatbot.adapter.ChatbotInboundEvent;
import com.geosun.tms.chatbot.adapter.ChatbotOutboundMessage;
import com.geosun.tms.chatbot.domain.BotIdentity;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.BotLinkCode;
import com.geosun.tms.chatbot.domain.BotMessageDirection;
import com.geosun.tms.chatbot.domain.BotMessageLog;
import com.geosun.tms.chatbot.domain.BotMessageStatus;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.chatbot.repository.BotLinkCodeRepository;
import com.geosun.tms.chatbot.repository.BotMessageLogRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Обробка вхідних подій бота: код прив'язки, contact verify, help/start/unlink.
 */
@Service
public class ChatbotEngine {

  private static final Logger log = LoggerFactory.getLogger(ChatbotEngine.class);
  private static final Pattern LINK_CODE = Pattern.compile("^[A-Z0-9]{8}$");

  private final BotLinkCodeRepository botLinkCodeRepository;
  private final BotIdentityRepository botIdentityRepository;
  private final BotMessageLogRepository botMessageLogRepository;
  private final UserContactPhoneRepository userContactPhoneRepository;
  private final ChatbotLinkService chatbotLinkService;
  private final UserRepository userRepository;

  public ChatbotEngine(
      BotLinkCodeRepository botLinkCodeRepository,
      BotIdentityRepository botIdentityRepository,
      BotMessageLogRepository botMessageLogRepository,
      UserContactPhoneRepository userContactPhoneRepository,
      ChatbotLinkService chatbotLinkService,
      UserRepository userRepository) {
    this.userRepository = userRepository;
    this.botLinkCodeRepository = botLinkCodeRepository;
    this.botIdentityRepository = botIdentityRepository;
    this.botMessageLogRepository = botMessageLogRepository;
    this.userContactPhoneRepository = userContactPhoneRepository;
    this.chatbotLinkService = chatbotLinkService;
  }

  @Transactional
  public void process(@NonNull ChatbotChannelAdapter adapter, @NonNull ChatbotInboundEvent event) {
    ChatbotChannel channel = adapter.channel();
    String externalUserId = Objects.requireNonNull(event.externalUserId());

    Optional<BotIdentity> identityOpt =
        botIdentityRepository.findByChannelAndExternalUserId(
            channel, Objects.requireNonNull(externalUserId));
    BotIdentity identity = identityOpt.orElse(null);

    logInbound(channel, identity, event);

    if (StringUtils.hasText(event.contactPhone())) {
      handleContact(adapter, event, identity);
      return;
    }

    String text = normalizeText(event.text());
    String command = event.command();
    if (!StringUtils.hasText(command) && StringUtils.hasText(text) && text.startsWith("/")) {
      String first = text.split("\\s+", 2)[0];
      int at = first.indexOf('@');
      command = (at > 0 ? first.substring(1, at) : first.substring(1)).toLowerCase(Locale.ROOT);
    }

    if ("help".equals(command)) {
      reply(adapter, externalUserId, identity, ChatbotMessages.HELP, false, "COMMAND_HELP");
      return;
    }

    if ("unlink".equals(command)) {
      handleUnlink(adapter, externalUserId, identity);
      return;
    }

    if ("start".equals(command)) {
      String codeFromStart = extractStartCode(text);
      if (StringUtils.hasText(codeFromStart)) {
        handleLinkCode(adapter, externalUserId, identity, Objects.requireNonNull(codeFromStart));
      } else if (identity != null && identity.getStatus() == BotIdentityStatus.ACTIVE) {
        reply(
            adapter, externalUserId, identity, ChatbotMessages.START_LINKED, true, "COMMAND_START");
      } else {
        reply(
            adapter,
            externalUserId,
            identity,
            ChatbotMessages.START_NEED_CODE,
            false,
            "COMMAND_START");
      }
      return;
    }

    if (StringUtils.hasText(text) && LINK_CODE.matcher(text).matches()) {
      handleLinkCode(adapter, externalUserId, identity, text);
      return;
    }

    reply(adapter, externalUserId, identity, ChatbotMessages.UNKNOWN, false, "UNKNOWN");
  }

  private void handleUnlink(
      ChatbotChannelAdapter adapter, String externalUserId, BotIdentity identity) {
    if (identity == null || identity.getStatus() != BotIdentityStatus.ACTIVE) {
      reply(
          adapter,
          externalUserId,
          identity,
          ChatbotMessages.UNLINK_NOT_LINKED,
          false,
          "COMMAND_UNLINK");
      return;
    }
    chatbotLinkService.revokeIdentity(identity);
    reply(adapter, externalUserId, null, ChatbotMessages.UNLINK_OK, false, "COMMAND_UNLINK");
    log.info("chatbot_unlinked channel={} userId={}", adapter.channel(), identity.getUserId());
  }

  private void handleLinkCode(
      ChatbotChannelAdapter adapter,
      String externalUserId,
      BotIdentity existingIdentity,
      @NonNull String codeRaw) {
    String code = Objects.requireNonNull(codeRaw.trim().toUpperCase(Locale.ROOT));
    Optional<BotLinkCode> linkOpt = botLinkCodeRepository.findByCode(Objects.requireNonNull(code));
    if (linkOpt.isEmpty()) {
      reply(
          adapter,
          externalUserId,
          existingIdentity,
          ChatbotMessages.LINK_INVALID_CODE,
          false,
          "LINK_FAILED");
      return;
    }
    BotLinkCode link = linkOpt.get();
    Instant now = Instant.now();
    if (link.getConsumedAt() != null || link.getExpiresAt().isBefore(now)) {
      reply(
          adapter,
          externalUserId,
          existingIdentity,
          ChatbotMessages.LINK_INVALID_CODE,
          false,
          "LINK_FAILED");
      return;
    }
    if (link.getChannel() != adapter.channel()) {
      reply(
          adapter,
          externalUserId,
          existingIdentity,
          ChatbotMessages.LINK_INVALID_CODE,
          false,
          "LINK_FAILED");
      return;
    }

    String linkUserId = Objects.requireNonNull(link.getUserId());
    String linkEnterpriseId =
        userRepository.findById(linkUserId).map(user -> user.getEnterpriseId()).orElse(null);

    Optional<BotIdentity> otherOpt =
        botIdentityRepository.findByChannelAndExternalUserId(
            adapter.channel(), Objects.requireNonNull(externalUserId));
    if (otherOpt.isPresent()) {
      BotIdentity otherIdentity = Objects.requireNonNull(otherOpt.get());
      if (!Objects.equals(otherIdentity.getUserId(), linkUserId)
          && otherIdentity.getStatus() == BotIdentityStatus.ACTIVE) {
        // Чат уже активний у користувача іншого підприємства — перепривʼязку забороняємо.
        String otherEnterpriseId = resolveIdentityEnterpriseId(otherIdentity);
        if (!Objects.equals(otherEnterpriseId, linkEnterpriseId)) {
          log.warn(
              "chatbot_link_rejected_cross_enterprise channel={} userId={}",
              adapter.channel(),
              linkUserId);
          reply(
              adapter,
              externalUserId,
              existingIdentity,
              ChatbotMessages.LINK_INVALID_CODE,
              false,
              "LINK_FAILED");
          return;
        }
        // Звільнити UNIQUE (channel, external_user_id) у межах того самого підприємства.
        chatbotLinkService.revokeIdentity(otherIdentity);
      }
    }

    BotIdentity identity =
        botIdentityRepository
            .findByUserIdAndChannel(linkUserId, adapter.channel())
            .orElseGet(() -> new BotIdentity());
    identity.setUserId(linkUserId);
    identity.setEnterpriseId(linkEnterpriseId);
    identity.setChannel(adapter.channel());
    identity.setExternalUserId(externalUserId);
    identity.setLocale("ua");
    identity.setStatus(BotIdentityStatus.ACTIVE);
    identity.setLinkedAt(now);
    identity.setRevokedAt(null);
    BotIdentity saved =
        Objects.requireNonNull(botIdentityRepository.save(Objects.requireNonNull(identity)));

    link.setConsumedAt(now);
    botLinkCodeRepository.save(Objects.requireNonNull(link));

    log.info("chatbot_linked channel={} userId={}", adapter.channel(), linkUserId);
    reply(adapter, externalUserId, saved, ChatbotMessages.ASK_SHARE_CONTACT, true, "LINK_OK");
  }

  /** Підприємство прив'язки: власне поле або (для старих рядків) підприємство користувача. */
  private String resolveIdentityEnterpriseId(@NonNull BotIdentity identity) {
    if (identity.getEnterpriseId() != null) {
      return identity.getEnterpriseId();
    }
    String userId = identity.getUserId();
    if (userId == null) {
      return null;
    }
    return userRepository.findById(userId).map(user -> user.getEnterpriseId()).orElse(null);
  }

  private void handleContact(
      ChatbotChannelAdapter adapter, ChatbotInboundEvent event, BotIdentity identity) {
    String externalUserId = Objects.requireNonNull(event.externalUserId());
    if (identity == null || identity.getStatus() != BotIdentityStatus.ACTIVE) {
      reply(
          adapter,
          externalUserId,
          identity,
          ChatbotMessages.CONTACT_NOT_LINKED,
          false,
          "CONTACT_REJECTED");
      return;
    }
    String contactTgId = event.contactTelegramUserId();
    if (contactTgId == null || !contactTgId.equals(externalUserId)) {
      reply(
          adapter,
          externalUserId,
          identity,
          ChatbotMessages.CONTACT_NOT_OWN,
          false,
          "CONTACT_REJECTED");
      return;
    }

    String normalizedPhone;
    try {
      normalizedPhone = normalizeContactPhone(event.contactPhone());
    } catch (IllegalArgumentException ex) {
      reply(
          adapter,
          externalUserId,
          identity,
          ChatbotMessages.CONTACT_PHONE_MISMATCH,
          true,
          "CONTACT_MISMATCH");
      return;
    }

    Optional<UserContactPhone> phoneOpt =
        userContactPhoneRepository.findByUserIdAndPhone(
            Objects.requireNonNull(identity.getUserId()), Objects.requireNonNull(normalizedPhone));
    if (phoneOpt.isEmpty()) {
      reply(
          adapter,
          externalUserId,
          identity,
          ChatbotMessages.CONTACT_PHONE_MISMATCH,
          true,
          "CONTACT_MISMATCH");
      return;
    }

    UserContactPhone phone = phoneOpt.get();
    phone.setPhoneVerified(true);
    phone.setPhoneVerifiedAt(Instant.now());
    phone.setPhoneVerifiedVia(ChatbotChannel.TELEGRAM.name());
    phone.setTelegram(true);
    userContactPhoneRepository.save(Objects.requireNonNull(phone));

    reply(
        adapter,
        externalUserId,
        identity,
        ChatbotMessages.CONTACT_VERIFIED,
        false,
        "CONTACT_VERIFIED");
  }

  private void reply(
      ChatbotChannelAdapter adapter,
      String externalUserId,
      BotIdentity identity,
      String text,
      boolean requestContact,
      String eventType) {
    BotMessageStatus status = BotMessageStatus.SENT;
    String providerError = null;
    try {
      adapter.send(
          new ChatbotOutboundMessage(
              Objects.requireNonNull(externalUserId),
              Objects.requireNonNull(text),
              requestContact));
    } catch (Exception ex) {
      status = BotMessageStatus.FAILED;
      providerError = truncate(ex.getMessage(), 256);
      log.warn("chatbot_outbound_failed: {}", ex.getMessage());
    }
    logOutbound(adapter.channel(), identity, eventType, text, status, providerError);
  }

  private void logInbound(ChatbotChannel channel, BotIdentity identity, ChatbotInboundEvent event) {
    BotMessageLog row = new BotMessageLog();
    row.setDirection(BotMessageDirection.IN);
    row.setChannel(channel);
    if (identity != null) {
      row.setIdentityId(identity.getId());
      row.setUserId(identity.getUserId());
    }
    row.setEventType(event.rawEventType());
    row.setStatus(BotMessageStatus.RECEIVED);
    String preview =
        StringUtils.hasText(event.contactPhone())
            ? "contact:" + PhoneE164Normalizer.maskForLog(safePreviewPhone(event.contactPhone()))
            : truncate(event.text(), 512);
    row.setPayloadPreview(preview);
    row.setAttemptCount(0);
    botMessageLogRepository.save(Objects.requireNonNull(row));
  }

  private void logOutbound(
      ChatbotChannel channel,
      BotIdentity identity,
      String eventType,
      String text,
      BotMessageStatus status,
      String providerError) {
    BotMessageLog row = new BotMessageLog();
    row.setDirection(BotMessageDirection.OUT);
    row.setChannel(channel);
    if (identity != null) {
      row.setIdentityId(identity.getId());
      row.setUserId(identity.getUserId());
    }
    row.setEventType(eventType);
    row.setStatus(status);
    row.setPayloadPreview(truncate(text, 512));
    row.setProviderError(providerError);
    row.setAttemptCount(1);
    if (status == BotMessageStatus.SENT) {
      row.setSentAt(Instant.now());
    }
    botMessageLogRepository.save(Objects.requireNonNull(row));
  }

  @NonNull
  private static String normalizeText(String text) {
    if (!StringUtils.hasText(text)) {
      return "";
    }
    return Objects.requireNonNull(text.trim().replace('\u00A0', ' '));
  }

  private static String extractStartCode(String text) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    String[] parts = text.trim().split("\\s+", 2);
    if (parts.length < 2) {
      return null;
    }
    String candidate = parts[1].trim().toUpperCase(Locale.ROOT);
    return LINK_CODE.matcher(candidate).matches() ? candidate : null;
  }

  @NonNull
  private static String normalizeContactPhone(String raw) {
    String digits = raw == null ? "" : raw.trim();
    if (!digits.startsWith("+")) {
      String onlyDigits = digits.replaceAll("[^0-9]", "");
      if (onlyDigits.isEmpty()) {
        throw new IllegalArgumentException("empty phone");
      }
      digits = "+" + onlyDigits;
    }
    return Objects.requireNonNull(PhoneE164Normalizer.normalize(digits));
  }

  private static String safePreviewPhone(String raw) {
    try {
      return normalizeContactPhone(raw);
    } catch (Exception ex) {
      return "****";
    }
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
