package com.geosun.tms.auth.notify;

import com.geosun.tms.auth.config.AppEmailProperties;
import com.geosun.tms.auth.config.AsyncConfig;
import com.geosun.tms.auth.config.UserRegisteredNotifyProperties;
import com.geosun.tms.auth.domain.profile.PhoneE164Normalizer;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.response.UserContactPhoneDto;
import com.geosun.tms.auth.dto.response.UserProfileDto;
import com.geosun.tms.auth.mail.AdminNotifyMailSender;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.service.UserProfileService;
import com.geosun.tms.auth.sms.SmsSender;
import com.geosun.tms.chatbot.service.ChatbotMessages;
import com.geosun.tms.chatbot.service.ChatbotOutboundNotifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Після успішної реєстрації: сповістити активних ADMIN каналами їхнього профілю.
 */
@Service
public class AdminNewUserNotifier {

  private static final Logger log = LoggerFactory.getLogger(AdminNewUserNotifier.class);

  private enum DeliveryChannel {
    EMAIL,
    PHONE,
    MESSENGERS
  }

  private final UserRegisteredNotifyProperties notifyProperties;
  private final UserRepository userRepository;
  private final UserProfileService userProfileService;
  private final AdminNotifyMailSender adminNotifyMailSender;
  private final SmsSender smsSender;
  private final ChatbotOutboundNotifier chatbotOutboundNotifier;
  private final AppEmailProperties appEmailProperties;

  public AdminNewUserNotifier(
      UserRegisteredNotifyProperties notifyProperties,
      UserRepository userRepository,
      UserProfileService userProfileService,
      AdminNotifyMailSender adminNotifyMailSender,
      SmsSender smsSender,
      ChatbotOutboundNotifier chatbotOutboundNotifier,
      AppEmailProperties appEmailProperties) {
    this.notifyProperties = notifyProperties;
    this.userRepository = userRepository;
    this.userProfileService = userProfileService;
    this.adminNotifyMailSender = adminNotifyMailSender;
    this.smsSender = smsSender;
    this.chatbotOutboundNotifier = chatbotOutboundNotifier;
    this.appEmailProperties = appEmailProperties;
  }

  /** Асинхронна розсилка після commit реєстрації. */
  @Async(AsyncConfig.ADMIN_NOTIFY_EXECUTOR)
  public void notifyAdminsAsync(
      @NonNull String newUserId, @NonNull String newUserEmail, @NonNull Instant createdAt) {
    try {
      notifyAdmins(newUserId, newUserEmail, createdAt);
    } catch (Exception ex) {
      log.error("admin_notify_failed newUserId={}: {}", newUserId, ex.getMessage());
    }
  }

  public void notifyAdmins(
      @NonNull String newUserId, @NonNull String newUserEmail, @NonNull Instant createdAt) {
    String userId = Objects.requireNonNull(newUserId);
    String email = Objects.requireNonNull(newUserEmail);
    Instant at = Objects.requireNonNull(createdAt);

    if (!notifyProperties.isEnabled()) {
      log.info("admin_notify_disabled newUserId={}", userId);
      return;
    }

    // Отримувачі — лише ADMIN того самого підприємства, що й новий користувач
    String enterpriseId =
        userRepository.findById(userId).map(user -> user.getEnterpriseId()).orElse(null);
    if (enterpriseId == null) {
      log.info("admin_notify_skipped_no_enterprise newUserId={}", userId);
      return;
    }
    List<User> admins =
        userRepository.findActiveWithAvailableRoleAndEnterpriseId(Role.ADMIN, enterpriseId);
    if (admins.isEmpty()) {
      log.info("admin_notify_queued newUserId={} recipients=0", userId);
      return;
    }

    log.info("admin_notify_queued newUserId={} recipients={}", userId, admins.size());
    List<String> adminIds = admins.stream().map(a -> Objects.requireNonNull(a.getId())).toList();
    Map<String, UserProfileDto> profiles = userProfileService.getByUserIds(adminIds);

    String cardLink = appEmailProperties.buildAdminUserCardLink(userId);
    String messengerText = ChatbotMessages.userRegistered(email, userId, cardLink);
    String smsText = buildSmsText(email, userId);

    for (User admin : admins) {
      try {
        notifyOneAdmin(
            admin, profiles.get(admin.getId()), userId, email, at, messengerText, smsText);
      } catch (Exception ex) {
        log.error(
            "admin_notify_admin_failed adminId={} newUserId={}: {}",
            admin.getId(),
            userId,
            ex.getMessage());
      }
    }
  }

  private void notifyOneAdmin(
      User admin,
      UserProfileDto profile,
      @NonNull String newUserId,
      @NonNull String newUserEmail,
      @NonNull Instant createdAt,
      @NonNull String messengerText,
      @NonNull String smsText) {
    Set<DeliveryChannel> channels = resolveChannels(profile);
    if (channels.contains(DeliveryChannel.EMAIL)) {
      sendEmail(admin, newUserId, newUserEmail, createdAt);
    }
    if (channels.contains(DeliveryChannel.PHONE)) {
      sendSms(admin, profile, smsText);
    }
    if (channels.contains(DeliveryChannel.MESSENGERS)) {
      chatbotOutboundNotifier.notifyUserRegistered(
          Objects.requireNonNull(admin.getId()),
          Objects.requireNonNull(newUserId),
          Objects.requireNonNull(messengerText));
    }
  }

  /** Канали з профілю або fallback EMAIL, якщо профіль порожній / без прапорців. */
  static Set<DeliveryChannel> resolveChannels(UserProfileDto profile) {
    if (profile == null) {
      return EnumSet.of(DeliveryChannel.EMAIL);
    }
    List<String> preferred =
        profile.preferredChannels() == null ? List.of() : profile.preferredChannels();
    if (preferred.isEmpty()) {
      return EnumSet.of(DeliveryChannel.EMAIL);
    }
    EnumSet<DeliveryChannel> channels = EnumSet.noneOf(DeliveryChannel.class);
    for (String raw : preferred) {
      if (raw == null) {
        continue;
      }
      switch (raw.trim().toUpperCase()) {
        case "EMAIL" -> channels.add(DeliveryChannel.EMAIL);
        case "PHONE" -> channels.add(DeliveryChannel.PHONE);
        case "MESSENGERS" -> channels.add(DeliveryChannel.MESSENGERS);
        default -> {
          // невідомий канал ігноруємо
        }
      }
    }
    if (channels.isEmpty()) {
      return EnumSet.of(DeliveryChannel.EMAIL);
    }
    return channels;
  }

  private void sendEmail(
      User admin,
      @NonNull String newUserId,
      @NonNull String newUserEmail,
      @NonNull Instant createdAt) {
    try {
      adminNotifyMailSender.sendNewUserRegistered(
          Objects.requireNonNull(admin.getEmail()),
          Objects.requireNonNull(newUserId),
          Objects.requireNonNull(newUserEmail),
          Objects.requireNonNull(createdAt));
      log.info("admin_notify_email_sent adminId={}", admin.getId());
    } catch (MailException ex) {
      log.error("admin_notify_email_failed adminId={}: {}", admin.getId(), ex.getMessage());
    }
  }

  private void sendSms(User admin, UserProfileDto profile, @NonNull String smsText) {
    String primary = findPrimaryPhone(profile);
    if (primary == null) {
      log.info("admin_notify_phone_skipped_no_number adminId={}", admin.getId());
      return;
    }
    if (!smsSender.enabled()) {
      log.info(
          "admin_notify_phone_skipped_no_provider adminId={} phone={}",
          admin.getId(),
          PhoneE164Normalizer.maskForLog(primary));
      return;
    }
    try {
      smsSender.send(Objects.requireNonNull(primary), Objects.requireNonNull(smsText));
      log.info(
          "admin_notify_sms_sent adminId={} phone={}",
          admin.getId(),
          PhoneE164Normalizer.maskForLog(primary));
    } catch (Exception ex) {
      log.error("admin_notify_sms_failed adminId={}: {}", admin.getId(), ex.getMessage());
    }
  }

  private static String findPrimaryPhone(UserProfileDto profile) {
    if (profile == null || profile.phones() == null || profile.phones().isEmpty()) {
      return null;
    }
    for (UserContactPhoneDto phone : profile.phones()) {
      if (phone != null && phone.primary() && phone.phone() != null && !phone.phone().isBlank()) {
        return phone.phone();
      }
    }
    UserContactPhoneDto first = profile.phones().get(0);
    return first != null ? first.phone() : null;
  }

  @NonNull
  static String buildSmsText(@NonNull String newUserEmail, @NonNull String newUserId) {
    String prefix = "GeoSun: нова реєстрація ";
    String suffix = " id=" + newUserId;
    int budget = 160 - prefix.length() - suffix.length();
    String email = Objects.requireNonNull(newUserEmail);
    if (budget < 8) {
      String shortText = prefix + suffix;
      return Objects.requireNonNull(shortText.substring(0, Math.min(160, shortText.length())));
    }
    if (email.length() > budget) {
      email = email.substring(0, Math.max(0, budget - 1)) + "…";
    }
    return Objects.requireNonNull(prefix + email + suffix);
  }

  /** Для unit-тестів: експорт resolved channels як імена. */
  static List<String> resolveChannelNames(UserProfileDto profile) {
    return resolveChannels(profile).stream()
        .map(ch -> Objects.requireNonNull(ch.name()))
        .sorted()
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
