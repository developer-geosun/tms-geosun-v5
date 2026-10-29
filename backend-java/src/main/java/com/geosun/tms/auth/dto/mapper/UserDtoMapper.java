package com.geosun.tms.auth.dto.mapper;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.domain.user.UserRoleRules;
import com.geosun.tms.auth.dto.response.RegisterResponse;
import com.geosun.tms.auth.dto.response.UserAdminDto;
import com.geosun.tms.auth.dto.response.UserProfileDto;
import com.geosun.tms.auth.dto.response.UserPublicDto;
import com.geosun.tms.auth.service.UserProfileService;
import java.util.List;
import java.util.Objects;

/**
 * Маппінг сутності {@link User} у DTO для API (без passwordHash).
 */
public final class UserDtoMapper {

  private UserDtoMapper() {}

  public static UserPublicDto toPublicDto(
      User user, UserProfileDto profile, String enterpriseCode) {
    if (user == null) {
      return null;
    }
    UserProfileDto safeProfile = profile == null ? UserProfileDto.empty() : profile;
    List<String> available = UserRoleRules.toSortedNames(user.snapshotAvailableRoles());
    return new UserPublicDto(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        available,
        enterpriseCode,
        UserProfileService.displayName(user.getEmail(), safeProfile),
        safeProfile);
  }

  public static UserAdminDto toAdminDto(User user, UserProfileDto profile) {
    Objects.requireNonNull(user);
    UserProfileDto safeProfile = profile == null ? UserProfileDto.empty() : profile;
    List<String> available = UserRoleRules.toSortedNames(user.snapshotAvailableRoles());
    return new UserAdminDto(
        Objects.requireNonNull(user.getId()),
        Objects.requireNonNull(user.getEmail()),
        Objects.requireNonNull(user.getRole()).name(),
        available,
        user.isActive(),
        user.isDeleted(),
        user.isEmailVerified(),
        Objects.requireNonNull(user.getCreatedAt()),
        Objects.requireNonNull(user.getUpdatedAt()),
        user.getDeletedAt(),
        UserProfileService.displayName(user.getEmail(), safeProfile),
        safeProfile);
  }

  public static RegisterResponse toRegisterResponse(User user) {
    if (user == null) {
      return null;
    }
    return new RegisterResponse(user.getId(), user.getEmail(), user.getRole().name());
  }
}
