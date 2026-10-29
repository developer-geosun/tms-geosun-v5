package com.geosun.tms.auth.notify;

import com.geosun.tms.auth.config.AppEmailProperties;
import com.geosun.tms.auth.config.AsyncConfig;
import com.geosun.tms.auth.config.FreightRequestCreatedNotifyProperties;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.response.UserProfileDto;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.service.UserProfileService;
import com.geosun.tms.chatbot.service.ChatbotOutboundNotifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Після створення заявки на фрахт: сповістити активних ADMIN з каналом MESSENGERS.
 */
@Service
public class AdminNewFreightRequestNotifier {

  private static final Logger log = LoggerFactory.getLogger(AdminNewFreightRequestNotifier.class);

  private final FreightRequestCreatedNotifyProperties notifyProperties;
  private final UserRepository userRepository;
  private final UserProfileService userProfileService;
  private final ChatbotOutboundNotifier chatbotOutboundNotifier;
  private final AppEmailProperties appEmailProperties;

  public AdminNewFreightRequestNotifier(
      FreightRequestCreatedNotifyProperties notifyProperties,
      UserRepository userRepository,
      UserProfileService userProfileService,
      ChatbotOutboundNotifier chatbotOutboundNotifier,
      AppEmailProperties appEmailProperties) {
    this.notifyProperties = notifyProperties;
    this.userRepository = userRepository;
    this.userProfileService = userProfileService;
    this.chatbotOutboundNotifier = chatbotOutboundNotifier;
    this.appEmailProperties = appEmailProperties;
  }

  /** Асинхронна розсилка після commit створення заявки (виклик через Spring-проксі). */
  @Async(AsyncConfig.ADMIN_NOTIFY_EXECUTOR)
  public void notifyAdminsAsync(
      @NonNull Long requestId, @NonNull String authorUserId, @NonNull String authorEmail) {
    try {
      notifyAdmins(
          Objects.requireNonNull(requestId),
          Objects.requireNonNull(authorUserId),
          Objects.requireNonNull(authorEmail));
    } catch (Exception ex) {
      log.error("freight_request_notify_failed requestId={}: {}", requestId, ex.getMessage());
    }
  }

  public void notifyAdmins(
      @NonNull Long requestId, @NonNull String authorUserId, @NonNull String authorEmail) {
    Long id = Objects.requireNonNull(requestId);
    String authorId = Objects.requireNonNull(authorUserId);
    String email = Objects.requireNonNull(authorEmail);

    if (!notifyProperties.isEnabled()) {
      log.info("freight_request_notify_disabled requestId={}", id);
      return;
    }

    // Отримувачі — лише ADMIN підприємства автора заявки
    String enterpriseId =
        userRepository.findById(authorId).map(user -> user.getEnterpriseId()).orElse(null);
    if (enterpriseId == null) {
      log.info("freight_request_notify_skipped_no_enterprise requestId={}", id);
      return;
    }
    List<User> admins =
        userRepository.findActiveWithAvailableRoleAndEnterpriseId(Role.ADMIN, enterpriseId).stream()
            .filter(admin -> admin.getId() != null && !authorId.equals(admin.getId()))
            .toList();
    if (admins.isEmpty()) {
      log.info("freight_request_notify_queued requestId={} recipients=0", id);
      return;
    }

    log.info("freight_request_notify_queued requestId={} recipients={}", id, admins.size());
    List<String> adminIds = admins.stream().map(a -> Objects.requireNonNull(a.getId())).toList();
    Map<String, UserProfileDto> profiles = userProfileService.getByUserIds(adminIds);
    String queueLink = appEmailProperties.buildAdminFreightRequestLink(id);

    for (User admin : admins) {
      try {
        notifyOneAdmin(admin, profiles.get(admin.getId()), id, email, queueLink);
      } catch (Exception ex) {
        log.error(
            "freight_request_notify_admin_failed adminId={} requestId={}: {}",
            admin.getId(),
            id,
            ex.getMessage());
      }
    }
  }

  private void notifyOneAdmin(
      User admin,
      UserProfileDto profile,
      @NonNull Long requestId,
      @NonNull String authorEmail,
      @NonNull String queueLink) {
    if (!hasMessengersChannel(profile)) {
      return;
    }
    chatbotOutboundNotifier.notifyFreightRequestCreated(
        Objects.requireNonNull(admin.getId()),
        Objects.requireNonNull(requestId),
        Objects.requireNonNull(authorEmail),
        Objects.requireNonNull(queueLink));
  }

  /** Лише явний прапорець MESSENGERS — без EMAIL-fallback. */
  static boolean hasMessengersChannel(UserProfileDto profile) {
    if (profile == null || profile.preferredChannels() == null) {
      return false;
    }
    for (String raw : profile.preferredChannels()) {
      if (raw != null && "MESSENGERS".equalsIgnoreCase(raw.trim())) {
        return true;
      }
    }
    return false;
  }

  /** Для unit-тестів: список id ADMIN після фільтра автора. */
  static List<String> filterAuthor(List<User> admins, @NonNull String authorUserId) {
    String authorId = Objects.requireNonNull(authorUserId);
    List<String> result = new ArrayList<>();
    for (User admin : admins) {
      if (admin.getId() != null && !authorId.equals(admin.getId())) {
        result.add(admin.getId());
      }
    }
    return result;
  }
}
