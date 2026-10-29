package com.geosun.tms.chatbot.service;

import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.dto.response.PageResponse;
import com.geosun.tms.chatbot.adapter.TelegramChannelAdapter;
import com.geosun.tms.chatbot.config.ChatbotProperties;
import com.geosun.tms.chatbot.domain.BotIdentity;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import com.geosun.tms.chatbot.dto.response.AdminBotIdentityDto;
import com.geosun.tms.chatbot.dto.response.AdminChatbotStatusDto;
import com.geosun.tms.chatbot.dto.response.ChannelStatusDto;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Адмінський статус каналів і список прив'язок. */
@Service
public class ChatbotAdminService {

  private final ChatbotProperties chatbotProperties;
  private final TelegramChannelAdapter telegramChannelAdapter;
  private final BotIdentityRepository botIdentityRepository;
  private final CurrentEnterprise currentEnterprise;

  public ChatbotAdminService(
      ChatbotProperties chatbotProperties,
      TelegramChannelAdapter telegramChannelAdapter,
      BotIdentityRepository botIdentityRepository,
      CurrentEnterprise currentEnterprise) {
    this.chatbotProperties = chatbotProperties;
    this.telegramChannelAdapter = telegramChannelAdapter;
    this.botIdentityRepository = botIdentityRepository;
    this.currentEnterprise = currentEnterprise;
  }

  @Transactional(readOnly = true)
  @NonNull
  public AdminChatbotStatusDto status() {
    ChatbotProperties.Telegram tg = chatbotProperties.getTelegram();
    boolean configured =
        StringUtils.hasText(tg.getBotToken()) && StringUtils.hasText(tg.getSecretToken());
    long active =
        botIdentityRepository.countByEnterpriseIdAndChannelAndStatus(
            currentEnterprise.requireId(), ChatbotChannel.TELEGRAM, BotIdentityStatus.ACTIVE);
    ChannelStatusDto telegram =
        new ChannelStatusDto(
            ChatbotChannel.TELEGRAM.name(), telegramChannelAdapter.enabled(), configured, active);
    return new AdminChatbotStatusDto(chatbotProperties.isEnabled(), List.of(telegram));
  }

  @Transactional(readOnly = true)
  @NonNull
  public PageResponse<AdminBotIdentityDto> listIdentities(
      int page, int size, @Nullable String channelRaw, @NonNull Role viewerRole) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(100, Math.max(1, size));
    PageRequest pageable = PageRequest.of(safePage, safeSize);
    String enterpriseId = Objects.requireNonNull(currentEnterprise.requireId());
    Page<BotIdentity> result;
    if (StringUtils.hasText(channelRaw)) {
      ChatbotChannel channel;
      try {
        channel =
            ChatbotChannel.valueOf(
                Objects.requireNonNull(channelRaw).trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return new PageResponse<>(List.of(), 0, 0, safePage, safeSize);
      }
      result =
          botIdentityRepository.findByEnterpriseIdAndChannel(
              enterpriseId, Objects.requireNonNull(channel), Objects.requireNonNull(pageable));
    } else {
      result =
          botIdentityRepository.findByEnterpriseId(enterpriseId, Objects.requireNonNull(pageable));
    }
    boolean mask = viewerRole != Role.ADMIN;
    List<AdminBotIdentityDto> content =
        result.getContent().stream()
            .map(row -> toAdminDto(Objects.requireNonNull(row), mask))
            .toList();
    return new PageResponse<>(
        content, result.getTotalElements(), result.getTotalPages(), safePage, safeSize);
  }

  @NonNull
  private static AdminBotIdentityDto toAdminDto(@NonNull BotIdentity row, boolean maskExternal) {
    String external = Objects.requireNonNull(row.getExternalUserId());
    if (maskExternal) {
      external = maskExternalId(external);
    }
    return new AdminBotIdentityDto(
        Objects.requireNonNull(row.getId()),
        Objects.requireNonNull(row.getUserId()),
        Objects.requireNonNull(row.getChannel()).name(),
        external,
        Objects.requireNonNull(row.getLocale()),
        Objects.requireNonNull(row.getStatus()).name(),
        row.getLinkedAt(),
        row.getRevokedAt());
  }

  @NonNull
  static String maskExternalId(@NonNull String externalUserId) {
    if (externalUserId.length() <= 4) {
      return "****";
    }
    return externalUserId.substring(0, 2)
        + "****"
        + externalUserId.substring(externalUserId.length() - 2);
  }
}
