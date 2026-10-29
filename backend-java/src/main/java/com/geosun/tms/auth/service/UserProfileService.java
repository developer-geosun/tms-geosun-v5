package com.geosun.tms.auth.service;

import com.geosun.tms.auth.domain.profile.ContactChannel;
import com.geosun.tms.auth.domain.profile.EdrpouChecksum;
import com.geosun.tms.auth.domain.profile.PersonNameSanitizer;
import com.geosun.tms.auth.domain.profile.PersonType;
import com.geosun.tms.auth.domain.profile.PhoneE164Normalizer;
import com.geosun.tms.auth.domain.profile.UserContactPhone;
import com.geosun.tms.auth.domain.profile.UserProfile;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.UpdateUserContactPhoneRequest;
import com.geosun.tms.auth.dto.request.UpdateUserProfileRequest;
import com.geosun.tms.auth.dto.response.UserContactPhoneDto;
import com.geosun.tms.auth.dto.response.UserProfileDto;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserContactPhoneRepository;
import com.geosun.tms.auth.repository.UserProfileRepository;
import com.geosun.tms.auth.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Читання/запис профілю облікового запису (окремі таблиці, без зміни users).
 */
@Service
public class UserProfileService {

  private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
  private static final int MAX_NAME_LENGTH = 128;
  private static final int MAX_PHONES = 5;

  private final UserRepository userRepository;
  private final UserProfileRepository userProfileRepository;
  private final UserContactPhoneRepository userContactPhoneRepository;

  public UserProfileService(
      UserRepository userRepository,
      UserProfileRepository userProfileRepository,
      UserContactPhoneRepository userContactPhoneRepository) {
    this.userRepository = userRepository;
    this.userProfileRepository = userProfileRepository;
    this.userContactPhoneRepository = userContactPhoneRepository;
  }

  @Transactional(readOnly = true)
  public UserProfileDto getByUserId(@NonNull String userId) {
    Optional<UserProfile> profile = userProfileRepository.findById(Objects.requireNonNull(userId));
    if (profile.isEmpty()) {
      return UserProfileDto.empty();
    }
    List<UserContactPhone> phones =
        userContactPhoneRepository.findByUserIdOrderBySortOrderAsc(Objects.requireNonNull(userId));
    return toDto(profile.get(), phones);
  }

  /** Batch-завантаження профілів для списку користувачів (без N+1). */
  @Transactional(readOnly = true)
  public Map<String, UserProfileDto> getByUserIds(Collection<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    List<UserProfile> profiles = userProfileRepository.findAllById(Objects.requireNonNull(userIds));
    Map<String, UserProfile> profileById =
        profiles.stream()
            .collect(
                Collectors.toMap(
                    p -> Objects.requireNonNull(p.getUserId()),
                    p -> Objects.requireNonNull(p),
                    (a, b) -> a,
                    HashMap::new));
    List<UserContactPhone> allPhones =
        userContactPhoneRepository.findByUserIdInOrderByUserIdAscSortOrderAsc(
            Objects.requireNonNull(userIds));
    Map<String, List<UserContactPhone>> phonesByUser = new HashMap<>();
    for (UserContactPhone phone : allPhones) {
      phonesByUser
          .computeIfAbsent(Objects.requireNonNull(phone.getUserId()), k -> new ArrayList<>())
          .add(phone);
    }
    Map<String, UserProfileDto> result = new HashMap<>();
    for (String userId : userIds) {
      UserProfile profile = profileById.get(userId);
      if (profile == null) {
        result.put(userId, UserProfileDto.empty());
      } else {
        result.put(userId, toDto(profile, phonesByUser.getOrDefault(userId, List.of())));
      }
    }
    return result;
  }

  @Transactional
  public UserProfileDto putSelf(@NonNull String userId, @NonNull UpdateUserProfileRequest request) {
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
    return upsert(userId, request);
  }

  @Transactional
  public UserProfileDto putAdmin(
      @NonNull String enterpriseId,
      @NonNull String targetUserId,
      @NonNull UpdateUserProfileRequest request) {
    User user = requireExistingUser(targetUserId, false);
    // Чужий користувач виглядає як неіснуючий
    if (!enterpriseId.equals(user.getEnterpriseId())) {
      throw ApiException.notFound("User not found");
    }
    if (user.isDeleted()) {
      throw ApiException.conflict("USER_DELETED", "User is deleted");
    }
    return upsert(targetUserId, request);
  }

  private User requireExistingUser(@NonNull String rawId, boolean rejectDeleted) {
    try {
      UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid user id");
    }
    User user =
        userRepository
            .findById(Objects.requireNonNull(rawId))
            .orElseThrow(() -> ApiException.notFound("User not found"));
    if (rejectDeleted && user.isDeleted()) {
      throw ApiException.conflict("USER_DELETED", "User is deleted");
    }
    return user;
  }

  private UserProfileDto upsert(@NonNull String userId, @NonNull UpdateUserProfileRequest request) {
    ValidatedProfile validated = validateAndNormalize(request);

    UserProfile profile =
        userProfileRepository
            .findById(Objects.requireNonNull(userId))
            .orElseGet(
                () -> {
                  UserProfile created = new UserProfile();
                  created.setUserId(userId);
                  return created;
                });

    profile.setLastName(validated.lastName());
    profile.setFirstName(validated.firstName());
    profile.setPatronymic(validated.patronymic());
    profile.setPersonType(validated.personType());
    profile.setLegalEntityEdrpou(validated.legalEntityEdrpou());
    profile.setContactViaEmail(validated.channels().contains(ContactChannel.EMAIL));
    profile.setContactViaPhone(validated.channels().contains(ContactChannel.PHONE));
    profile.setContactViaMessengers(validated.channels().contains(ContactChannel.MESSENGERS));

    userProfileRepository.save(Objects.requireNonNull(profile));

    // Зберігаємо прапорці верифікації для тих самих E.164 до deleteAll (клієнт їх не шле).
    List<UserContactPhone> existingPhones =
        userContactPhoneRepository.findByUserIdOrderBySortOrderAsc(Objects.requireNonNull(userId));
    Map<String, UserContactPhone> verifiedByPhone = new HashMap<>();
    for (UserContactPhone existing : existingPhones) {
      if (existing.isPhoneVerified() && StringUtils.hasText(existing.getPhone())) {
        verifiedByPhone.put(Objects.requireNonNull(existing.getPhone()), existing);
      }
    }

    userContactPhoneRepository.deleteAllByUserId(Objects.requireNonNull(userId));
    List<UserContactPhone> savedPhones = new ArrayList<>();
    for (int i = 0; i < validated.phones().size(); i++) {
      ValidatedPhone vp = validated.phones().get(i);
      UserContactPhone row = new UserContactPhone();
      if (StringUtils.hasText(vp.id())) {
        try {
          UUID.fromString(vp.id());
          row.setId(vp.id());
        } catch (IllegalArgumentException ex) {
          // Невалідний id — новий UUID через @PrePersist
        }
      }
      row.setUserId(userId);
      row.setPhone(vp.phone());
      row.setSortOrder(i);
      row.setPrimary(vp.primary());
      row.setTelegram(vp.telegram());
      row.setWhatsapp(vp.whatsapp());
      row.setViber(vp.viber());
      UserContactPhone previouslyVerified = verifiedByPhone.get(vp.phone());
      if (previouslyVerified != null) {
        row.setPhoneVerified(true);
        row.setPhoneVerifiedAt(previouslyVerified.getPhoneVerifiedAt());
        row.setPhoneVerifiedVia(previouslyVerified.getPhoneVerifiedVia());
      }
      savedPhones.add(
          Objects.requireNonNull(userContactPhoneRepository.save(Objects.requireNonNull(row))));
      log.debug(
          "Saved contact phone for user {}: {}",
          userId,
          PhoneE164Normalizer.maskForLog(vp.phone()));
    }

    return toDto(profile, savedPhones);
  }

  private ValidatedProfile validateAndNormalize(UpdateUserProfileRequest request) {
    String lastName = requireSanitizedName(request.lastName(), "lastName");
    String firstName = requireSanitizedName(request.firstName(), "firstName");
    String patronymic = optionalSanitizedName(request.patronymic());

    PersonType personType = parsePersonType(request.personType());
    String edrpou = normalizeEdrpou(request.legalEntityEdrpou(), personType);

    List<ContactChannel> channels = parseChannels(request.preferredChannels());
    List<ValidatedPhone> phones = normalizePhones(request.phones());

    if (channels.contains(ContactChannel.PHONE) && phones.isEmpty()) {
      throw ApiException.badRequest(
          "PROFILE_CHANNEL_PHONE_REQUIRED", "PHONE channel requires at least one phone");
    }
    if (channels.contains(ContactChannel.MESSENGERS)) {
      boolean anyMessenger =
          phones.stream().anyMatch(p -> p.telegram() || p.whatsapp() || p.viber());
      if (!anyMessenger) {
        throw ApiException.badRequest(
            "PROFILE_CHANNEL_MESSENGER_REQUIRED",
            "MESSENGERS channel requires at least one messenger flag");
      }
    }

    return new ValidatedProfile(
        lastName, firstName, patronymic, personType, edrpou, channels, phones);
  }

  private static String requireSanitizedName(String raw, String field) {
    String sanitized = PersonNameSanitizer.sanitize(raw);
    if (!StringUtils.hasText(sanitized)) {
      throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
    }
    if (sanitized.length() > MAX_NAME_LENGTH) {
      throw ApiException.badRequest("VALIDATION_ERROR", field + " exceeds max length");
    }
    return sanitized;
  }

  private static String optionalSanitizedName(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String sanitized = PersonNameSanitizer.sanitize(raw);
    if (!StringUtils.hasText(sanitized)) {
      return null;
    }
    if (sanitized.length() > MAX_NAME_LENGTH) {
      throw ApiException.badRequest("VALIDATION_ERROR", "patronymic exceeds max length");
    }
    return sanitized;
  }

  private static PersonType parsePersonType(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "personType is required");
    }
    try {
      return PersonType.valueOf(raw.trim());
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid personType");
    }
  }

  private static String normalizeEdrpou(String raw, PersonType personType) {
    boolean blank = !StringUtils.hasText(raw) || EdrpouChecksum.normalizeDigits(raw).isEmpty();
    if (personType == PersonType.INDIVIDUAL) {
      if (!blank) {
        throw ApiException.badRequest(
            "PROFILE_EDRPOU_FORBIDDEN", "INDIVIDUAL must not have legalEntityEdrpou");
      }
      return null;
    }
    if (blank) {
      throw ApiException.badRequest(
          "VALIDATION_ERROR", "legalEntityEdrpou is required for LEGAL_ENTITY_REPRESENTATIVE");
    }
    String digits = EdrpouChecksum.normalizeDigits(raw);
    if (digits.length() != 8 && digits.length() != 10) {
      throw ApiException.badRequest("VALIDATION_ERROR", "legalEntityEdrpou must be 8 or 10 digits");
    }
    if (!EdrpouChecksum.isValid(digits)) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid legalEntityEdrpou checksum");
    }
    return digits;
  }

  private static List<ContactChannel> parseChannels(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "preferredChannels requires at least one");
    }
    LinkedHashMap<ContactChannel, Boolean> ordered = new LinkedHashMap<>();
    for (String item : raw) {
      if (!StringUtils.hasText(item)) {
        continue;
      }
      try {
        ordered.put(ContactChannel.valueOf(item.trim()), Boolean.TRUE);
      } catch (IllegalArgumentException ex) {
        throw ApiException.badRequest("VALIDATION_ERROR", "Invalid preferredChannels value");
      }
    }
    if (ordered.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "preferredChannels requires at least one");
    }
    return List.copyOf(ordered.keySet());
  }

  private static List<ValidatedPhone> normalizePhones(List<UpdateUserContactPhoneRequest> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    if (raw.size() > MAX_PHONES) {
      throw ApiException.badRequest("VALIDATION_ERROR", "At most 5 phones allowed");
    }

    List<ValidatedPhone> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int primaryCount = 0;
    boolean anyExplicitPrimary = false;

    for (UpdateUserContactPhoneRequest item : raw) {
      if (item == null) {
        throw ApiException.badRequest("VALIDATION_ERROR", "Phone entry is required");
      }
      String phone;
      try {
        phone = PhoneE164Normalizer.normalize(item.phone());
      } catch (IllegalArgumentException ex) {
        throw ApiException.badRequest("VALIDATION_ERROR", ex.getMessage());
      }
      if (!seen.add(phone)) {
        throw ApiException.badRequest(
            "PROFILE_PHONE_DUPLICATE", "Duplicate phone within the same profile");
      }
      Boolean primaryFlag = item.primary();
      if (primaryFlag != null) {
        anyExplicitPrimary = true;
        if (primaryFlag) {
          primaryCount++;
        }
      }
      result.add(
          new ValidatedPhone(
              StringUtils.hasText(item.id()) ? item.id().trim() : null,
              phone,
              Boolean.TRUE.equals(primaryFlag),
              Boolean.TRUE.equals(item.telegram()),
              Boolean.TRUE.equals(item.whatsapp()),
              Boolean.TRUE.equals(item.viber())));
    }

    if (anyExplicitPrimary && primaryCount > 1) {
      throw ApiException.badRequest(
          "PROFILE_PRIMARY_PHONE_INVALID", "Exactly one primary phone is required");
    }
    if (primaryCount == 0) {
      // Жодного primary — перший стає primary
      ValidatedPhone first = result.get(0);
      result.set(
          0,
          new ValidatedPhone(
              first.id(), first.phone(), true, first.telegram(), first.whatsapp(), first.viber()));
    } else if (!anyExplicitPrimary) {
      // не повинно статись
    }
    return List.copyOf(result);
  }

  public static UserProfileDto toDto(UserProfile profile, List<UserContactPhone> phones) {
    List<String> channels = new ArrayList<>();
    if (profile.isContactViaEmail()) {
      channels.add(ContactChannel.EMAIL.name());
    }
    if (profile.isContactViaPhone()) {
      channels.add(ContactChannel.PHONE.name());
    }
    if (profile.isContactViaMessengers()) {
      channels.add(ContactChannel.MESSENGERS.name());
    }
    List<UserContactPhoneDto> phoneDtos =
        phones.stream()
            .map(
                p -> {
                  UserContactPhone phone = Objects.requireNonNull(p);
                  return new UserContactPhoneDto(
                      Objects.requireNonNull(phone.getId()),
                      Objects.requireNonNull(phone.getPhone()),
                      phone.isPrimary(),
                      phone.isTelegram(),
                      phone.isWhatsapp(),
                      phone.isViber(),
                      phone.isPhoneVerified(),
                      phone.getPhoneVerifiedAt(),
                      phone.getPhoneVerifiedVia());
                })
            .toList();
    boolean complete = isProfileComplete(profile, phones, channels);
    return new UserProfileDto(
        profile.getLastName(),
        profile.getFirstName(),
        profile.getPatronymic(),
        profile.getPersonType() == null ? null : profile.getPersonType().name(),
        profile.getLegalEntityEdrpou(),
        List.copyOf(channels),
        phoneDtos,
        complete);
  }

  public static boolean isProfileComplete(
      UserProfile profile, List<UserContactPhone> phones, List<String> channels) {
    if (!StringUtils.hasText(profile.getLastName())
        || !StringUtils.hasText(profile.getFirstName())) {
      return false;
    }
    if (profile.getPersonType() == null) {
      return false;
    }
    if (profile.getPersonType() == PersonType.LEGAL_ENTITY_REPRESENTATIVE) {
      if (!EdrpouChecksum.isValid(profile.getLegalEntityEdrpou())) {
        return false;
      }
    } else if (StringUtils.hasText(profile.getLegalEntityEdrpou())) {
      return false;
    }
    if (channels == null || channels.isEmpty()) {
      return false;
    }
    if (channels.contains(ContactChannel.PHONE.name()) && (phones == null || phones.isEmpty())) {
      return false;
    }
    if (channels.contains(ContactChannel.MESSENGERS.name())) {
      boolean any =
          phones != null
              && phones.stream().anyMatch(p -> p.isTelegram() || p.isWhatsapp() || p.isViber());
      if (!any) {
        return false;
      }
    }
    return true;
  }

  public static String displayName(String email, UserProfileDto profile) {
    if (profile == null) {
      return email;
    }
    List<String> parts = new ArrayList<>();
    if (StringUtils.hasText(profile.lastName())) {
      parts.add(profile.lastName().trim());
    }
    if (StringUtils.hasText(profile.firstName())) {
      parts.add(profile.firstName().trim());
    }
    if (StringUtils.hasText(profile.patronymic())) {
      parts.add(profile.patronymic().trim());
    }
    return parts.isEmpty() ? email : String.join(" ", parts);
  }

  private record ValidatedPhone(
      String id,
      String phone,
      boolean primary,
      boolean telegram,
      boolean whatsapp,
      boolean viber) {}

  private record ValidatedProfile(
      String lastName,
      String firstName,
      String patronymic,
      PersonType personType,
      String legalEntityEdrpou,
      List<ContactChannel> channels,
      List<ValidatedPhone> phones) {}
}
