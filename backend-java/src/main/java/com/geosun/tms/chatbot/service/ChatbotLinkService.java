package com.geosun.tms.chatbot.service;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserContactPhoneRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.chatbot.adapter.TelegramChannelAdapter;
import com.geosun.tms.chatbot.config.ChatbotProperties;
import com.geosun.tms.chatbot.domain.BotIdentity;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.BotLinkCode;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import com.geosun.tms.chatbot.dto.response.BotIdentitiesSelfResponse;
import com.geosun.tms.chatbot.dto.response.BotIdentitySelfDto;
import com.geosun.tms.chatbot.dto.response.BotLinkCodeResponse;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.chatbot.repository.BotLinkCodeRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Створення кодів прив'язки, список і відв'язка для self-API. */
@Service
public class ChatbotLinkService {

  private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int CODE_LENGTH = 8;
  private static final int CODE_TTL_MINUTES = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final UserContactPhoneRepository userContactPhoneRepository;
  private final BotLinkCodeRepository botLinkCodeRepository;
  private final BotIdentityRepository botIdentityRepository;
  private final ChatbotProperties chatbotProperties;
  private final TelegramChannelAdapter telegramChannelAdapter;

  public ChatbotLinkService(
      UserRepository userRepository,
      UserContactPhoneRepository userContactPhoneRepository,
      BotLinkCodeRepository botLinkCodeRepository,
      BotIdentityRepository botIdentityRepository,
      ChatbotProperties chatbotProperties,
      TelegramChannelAdapter telegramChannelAdapter) {
    this.userRepository = userRepository;
    this.userContactPhoneRepository = userContactPhoneRepository;
    this.botLinkCodeRepository = botLinkCodeRepository;
    this.botIdentityRepository = botIdentityRepository;
    this.chatbotProperties = chatbotProperties;
    this.telegramChannelAdapter = telegramChannelAdapter;
  }

  @Transactional
  @NonNull
  public BotLinkCodeResponse createLinkCode(@NonNull String userId, @NonNull String channelRaw) {
    User user =
        userRepository
            .findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> ApiException.notFound("User not found"));
    if (user.isDeleted()) {
      throw ApiException.conflict("USER_DELETED", "User is deleted");
    }
    if (!user.isActive()) {
      throw ApiException.forbidden("FORBIDDEN", "User is inactive");
    }

    ChatbotChannel channel = parseChannel(channelRaw);
    if (channel != ChatbotChannel.TELEGRAM || !telegramChannelAdapter.enabled()) {
      throw ApiException.conflict("BOT_CHANNEL_DISABLED", "Bot channel is disabled");
    }

    botIdentityRepository
        .findByUserIdAndChannel(Objects.requireNonNull(userId), channel)
        .filter(id -> id.getStatus() == BotIdentityStatus.ACTIVE)
        .ifPresent(
            id -> {
              throw ApiException.conflict(
                  "BOT_ALREADY_LINKED", "Bot identity already linked for this channel");
            });

    String code = generateUniqueCode();
    Instant expiresAt = Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES);
    BotLinkCode row = new BotLinkCode();
    row.setUserId(userId);
    row.setEnterpriseId(user.getEnterpriseId());
    row.setChannel(channel);
    row.setCode(code);
    row.setExpiresAt(expiresAt);
    botLinkCodeRepository.save(Objects.requireNonNull(row));

    String username = chatbotProperties.getTelegram().getBotUsername();
    String deepLink =
        StringUtils.hasText(username)
            ? "https://t.me/" + username.replace("@", "") + "?start=" + code
            : null;

    return new BotLinkCodeResponse(
        Objects.requireNonNull(channel.name()), Objects.requireNonNull(code), expiresAt, deepLink);
  }

  @Transactional(readOnly = true)
  @NonNull
  public BotIdentitiesSelfResponse listIdentities(@NonNull String userId) {
    boolean phoneVerifiedAny =
        userContactPhoneRepository.existsByUserIdAndPhoneVerifiedTrue(
            Objects.requireNonNull(userId));
    List<BotIdentitySelfDto> items =
        botIdentityRepository.findByUserId(Objects.requireNonNull(userId)).stream()
            .map(
                row -> {
                  BotIdentity identity = Objects.requireNonNull(row);
                  return new BotIdentitySelfDto(
                      Objects.requireNonNull(identity.getChannel()).name(),
                      Objects.requireNonNull(identity.getStatus()).name(),
                      Objects.requireNonNull(identity.getLocale()),
                      identity.getLinkedAt(),
                      phoneVerifiedAny);
                })
            .toList();
    return new BotIdentitiesSelfResponse(items, phoneVerifiedAny);
  }

  @Transactional
  public void unlink(@NonNull String userId, @NonNull String channelRaw) {
    ChatbotChannel channel = parseChannel(channelRaw);
    botIdentityRepository
        .findByUserIdAndChannel(Objects.requireNonNull(userId), channel)
        .ifPresent(identity -> revokeIdentity(Objects.requireNonNull(identity)));
  }

  /** Відкликає прив'язку (з сайту або команди unlink у чаті). */
  @Transactional
  public void revokeIdentity(@NonNull BotIdentity identity) {
    if (identity.getStatus() == BotIdentityStatus.REVOKED) {
      return;
    }
    identity.setStatus(BotIdentityStatus.REVOKED);
    identity.setExternalUserId("revoked:" + UUID.randomUUID());
    identity.setRevokedAt(Instant.now());
    botIdentityRepository.save(Objects.requireNonNull(identity));
  }

  @NonNull
  private static ChatbotChannel parseChannel(@NonNull String raw) {
    if (!StringUtils.hasText(raw)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "channel is required");
    }
    try {
      return Objects.requireNonNull(ChatbotChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid channel");
    }
  }

  @NonNull
  private String generateUniqueCode() {
    for (int attempt = 0; attempt < 32; attempt++) {
      StringBuilder sb = new StringBuilder(CODE_LENGTH);
      for (int i = 0; i < CODE_LENGTH; i++) {
        sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
      }
      String code = sb.toString();
      if (!botLinkCodeRepository.existsByCode(Objects.requireNonNull(code))) {
        return code;
      }
    }
    throw ApiException.conflict("CONFLICT", "Could not allocate unique link code");
  }
}
