package com.geosun.tms.auth.service;

import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.mapper.UserDtoMapper;
import com.geosun.tms.auth.dto.request.AdminUserListQuery;
import com.geosun.tms.auth.dto.response.PageResponse;
import com.geosun.tms.auth.dto.response.UserAdminDto;
import com.geosun.tms.auth.dto.response.UserProfileDto;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.repository.UserSpecifications;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Адмін-операції над користувачами: список, роль, active, soft-delete, restore.
 */
@Service
public class AdminUserService {

  private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "updatedAt", "email", "role");

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final SuperAdminPasswordService superAdminPasswordService;
  private final UserProfileService userProfileService;

  public AdminUserService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      SuperAdminPasswordService superAdminPasswordService,
      UserProfileService userProfileService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.superAdminPasswordService = superAdminPasswordService;
    this.userProfileService = userProfileService;
  }

  @Transactional(readOnly = true)
  public PageResponse<UserAdminDto> list(@NonNull String enterpriseId, AdminUserListQuery query) {
    int page = Math.max(0, query.page());
    int size = Math.min(100, Math.max(1, query.size()));
    Boolean deleted = query.deleted() == null ? Boolean.FALSE : query.deleted();
    Sort sort = resolveSort(query.sort(), query.order());
    Page<User> result =
        userRepository.findAll(
            UserSpecifications.adminFilter(
                enterpriseId, query.email(), query.name(), query.role(), query.active(), deleted),
            PageRequest.of(page, size, sort));
    List<String> ids =
        result.getContent().stream().map(u -> Objects.requireNonNull(u.getId())).toList();
    Map<String, UserProfileDto> profiles = userProfileService.getByUserIds(ids);
    List<UserAdminDto> content =
        result.getContent().stream()
            .map(
                user ->
                    UserDtoMapper.toAdminDto(
                        Objects.requireNonNull(user),
                        profiles.getOrDefault(user.getId(), UserProfileDto.empty())))
            .toList();
    return new PageResponse<>(
        content,
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public UserAdminDto getById(@NonNull String enterpriseId, @NonNull String rawId) {
    User user = requireUser(enterpriseId, rawId);
    return UserDtoMapper.toAdminDto(
        user, userProfileService.getByUserId(Objects.requireNonNull(user.getId())));
  }

  /**
   * Призначає повний набір available roles. Active лишається, якщо входить у набір; інакше —
   * preferred (USER → …).
   */
  @Transactional
  public UserAdminDto updateRoles(
      @NonNull String enterpriseId,
      @NonNull String actorUserId,
      @NonNull String rawId,
      @NonNull Set<Role> roles,
      String superAdminPassword) {
    if (roles.isEmpty()) {
      throw ApiException.badRequest("ROLES_REQUIRED", "At least one role is required");
    }
    User user = requireUser(enterpriseId, rawId);
    assertNotSelf(actorUserId, Objects.requireNonNull(user.getId()));
    assertNotDeleted(user);

    Set<Role> previous = user.snapshotAvailableRoles();
    Set<Role> next = EnumSet.copyOf(roles);
    if (previous.equals(next) && next.contains(user.getRole())) {
      return toDto(user);
    }

    boolean removingAdmin = previous.contains(Role.ADMIN) && !next.contains(Role.ADMIN);
    if (removingAdmin) {
      assertNotLastActiveAdmin(enterpriseId, user);
      // Зняття ролі ADMIN — лише після коректного пароля суперадміна
      superAdminPasswordService.requireValid(superAdminPassword);
    }

    user.replaceAvailableRoles(Objects.requireNonNull(next));
    refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    return toDto(userRepository.save(Objects.requireNonNull(user)));
  }

  /** Сумісність: singleton-набір через {@link #updateRoles}. */
  @Transactional
  public UserAdminDto updateRole(
      @NonNull String enterpriseId,
      @NonNull String actorUserId,
      @NonNull String rawId,
      @NonNull Role newRole,
      String superAdminPassword) {
    return updateRoles(
        enterpriseId,
        actorUserId,
        rawId,
        Objects.requireNonNull(EnumSet.of(newRole)),
        superAdminPassword);
  }

  @Transactional
  public UserAdminDto setActive(
      @NonNull String enterpriseId,
      @NonNull String actorUserId,
      @NonNull String rawId,
      boolean active) {
    User user = requireUser(enterpriseId, rawId);
    assertNotSelf(actorUserId, user.getId());
    assertNotDeleted(user);
    if (user.isActive() == active) {
      return toDto(user);
    }
    if (!active && user.hasAvailableRole(Role.ADMIN)) {
      assertNotLastActiveAdmin(enterpriseId, user);
    }
    user.setActive(active);
    if (!active) {
      refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }
    return toDto(userRepository.save(Objects.requireNonNull(user)));
  }

  @Transactional
  public void softDelete(
      @NonNull String enterpriseId, @NonNull String actorUserId, @NonNull String rawId) {
    User user = requireUser(enterpriseId, rawId);
    if (user.isDeleted()) {
      return;
    }
    assertNotSelf(actorUserId, user.getId());
    if (user.hasAvailableRole(Role.ADMIN) && user.isActive()) {
      assertNotLastActiveAdmin(enterpriseId, user);
    }
    user.setDeleted(true);
    user.setDeletedAt(Instant.now());
    user.setActive(false);
    refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    userRepository.save(Objects.requireNonNull(user));
  }

  /**
   * Відновлення після soft-delete: знімає deleted, знову активує акаунт.
   * Ідемпотентно, якщо вже не видалений. Конфлікт email — якщо в тому самому підприємстві є інший активний з тим самим email.
   */
  @Transactional
  public UserAdminDto restore(
      @NonNull String enterpriseId, @NonNull String actorUserId, @NonNull String rawId) {
    User user = requireUser(enterpriseId, rawId);
    assertNotSelf(actorUserId, user.getId());
    if (!user.isDeleted()) {
      return toDto(user);
    }
    if (userRepository.existsByEnterpriseIdAndEmailAndDeletedFalse(enterpriseId, user.getEmail())) {
      throw ApiException.conflict(
          "EMAIL_ALREADY_EXISTS", "Cannot restore: another active user already uses this email");
    }
    user.setDeleted(false);
    user.setDeletedAt(null);
    user.setActive(true);
    return toDto(userRepository.save(Objects.requireNonNull(user)));
  }

  /** Користувач лише з підприємства викликача; чужий id дає 404, як неіснуючий. */
  private User requireUser(@NonNull String enterpriseId, @NonNull String rawId) {
    try {
      UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Invalid user id");
    }
    User user =
        userRepository.findById(rawId).orElseThrow(() -> ApiException.notFound("User not found"));
    if (!enterpriseId.equals(user.getEnterpriseId())) {
      throw ApiException.notFound("User not found");
    }
    return user;
  }

  private void assertNotSelf(String actorUserId, String targetUserId) {
    if (actorUserId.equals(targetUserId)) {
      throw ApiException.badRequest(
          "SELF_OPERATION_FORBIDDEN", "Cannot perform this operation on your own account");
    }
  }

  private void assertNotDeleted(User user) {
    if (user.isDeleted()) {
      throw ApiException.conflict("USER_DELETED", "User is deleted");
    }
  }

  private void assertNotLastActiveAdmin(@NonNull String enterpriseId, User target) {
    long activeAdmins =
        userRepository.countActiveWithAvailableRoleAndEnterpriseId(Role.ADMIN, enterpriseId);
    if (activeAdmins <= 1
        && target.hasAvailableRole(Role.ADMIN)
        && target.isActive()
        && !target.isDeleted()) {
      throw ApiException.conflict(
          "LAST_ADMIN_PROTECTED", "Cannot modify the last active ADMIN account");
    }
  }

  private @NonNull Sort resolveSort(String sort, String order) {
    String property = StringUtils.hasText(sort) && ALLOWED_SORT.contains(sort) ? sort : "createdAt";
    boolean asc = "asc".equalsIgnoreCase(order);
    return Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, property);
  }

  private UserAdminDto toDto(User user) {
    return UserDtoMapper.toAdminDto(
        user, userProfileService.getByUserId(Objects.requireNonNull(user.getId())));
  }
}
