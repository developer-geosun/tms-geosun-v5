package com.geosun.tms.auth.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.SwitchRoleRequest;
import com.geosun.tms.auth.dto.request.UpdateUserActiveRequest;
import com.geosun.tms.auth.dto.request.UpdateUserRoleRequest;
import com.geosun.tms.auth.dto.request.UpdateUserRolesRequest;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.service.AdminUserService;
import com.geosun.tms.support.TestEnterprise;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Інтеграційні сценарії admin user management.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private AdminUserService adminUserService;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void managerCanListUsers_userForbidden() throws Exception {
    User manager = saveUser("manager-adm@example.com", "Secret123", Role.MANAGER);
    User plain = saveUser("user-adm@example.com", "Secret123", Role.USER);
    Session managerSession = login(manager.getEmail(), "Secret123");
    Session userSession = login(plain.getEmail(), "Secret123");

    mockMvc
        .perform(
            get("/api/v1/admin/users").header("Authorization", "Bearer " + managerSession.access()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/admin/users").header("Authorization", "Bearer " + userSession.access()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void admin_listFilterAndGetById() throws Exception {
    User admin = saveUser("admin-list@example.com", "Admin123!", Role.ADMIN);
    User target = saveUser("target-list@example.com", "Secret123", Role.USER);
    Session adminSession = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            get("/api/v1/admin/users")
                .param("email", "target-list")
                .param("role", "USER")
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value("target-list@example.com"))
        .andExpect(jsonPath("$.content[0].role").value("USER"));

    mockMvc
        .perform(
            get("/api/v1/admin/users/" + target.getId())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(target.getId()))
        .andExpect(jsonPath("$.email").value("target-list@example.com"));
  }

  @Test
  void admin_updateRole_andRevokesRefresh() throws Exception {
    User admin = saveUser("admin-role@example.com", "Admin123!", Role.ADMIN);
    User target = saveUser("target-role@example.com", "Secret123", Role.USER);
    Session adminSession = login(admin.getEmail(), "Admin123!");
    Session targetSession = login(target.getEmail(), "Secret123");

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + target.getId() + "/role")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(toJson(new UpdateUserRoleRequest(Role.MANAGER, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(targetSession.refresh()))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void admin_deactivate_revokesRefresh() throws Exception {
    User admin = saveUser("admin-active@example.com", "Admin123!", Role.ADMIN);
    User target = saveUser("target-active@example.com", "Secret123", Role.USER);
    Session adminSession = login(admin.getEmail(), "Admin123!");
    Session targetSession = login(target.getEmail(), "Secret123");

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + target.getId() + "/active")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(toJson(new UpdateUserActiveRequest(false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(targetSession.refresh()))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void admin_softDelete_idempotent() throws Exception {
    User admin = saveUser("admin-del@example.com", "Admin123!", Role.ADMIN);
    User victim = saveUser("victim-del@example.com", "Secret123", Role.USER);
    Session adminSession = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            delete("/api/v1/admin/users/" + victim.getId())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete("/api/v1/admin/users/" + victim.getId())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/v1/admin/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void admin_restore_afterSoftDelete() throws Exception {
    User admin = saveUser("admin-restore@example.com", "Admin123!", Role.ADMIN);
    User victim = saveUser("victim-restore@example.com", "Secret123", Role.USER);
    Session adminSession = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            delete("/api/v1/admin/users/" + victim.getId())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(new LoginRequest(TestEnterprise.CODE, victim.getEmail(), "Secret123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_DELETED"));

    mockMvc
        .perform(
            post("/api/v1/admin/users/" + victim.getId() + "/restore")
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(new LoginRequest(TestEnterprise.CODE, victim.getEmail(), "Secret123"))))
        .andExpect(status().isOk());
  }

  @Test
  void admin_selfOperation_forbidden() throws Exception {
    User admin = saveUser("admin-self@example.com", "Admin123!", Role.ADMIN);
    Session adminSession = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + admin.getId() + "/role")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(toJson(new UpdateUserRoleRequest(Role.USER, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_OPERATION_FORBIDDEN"));

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + admin.getId() + "/active")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(toJson(new UpdateUserActiveRequest(false))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_OPERATION_FORBIDDEN"));

    mockMvc
        .perform(
            delete("/api/v1/admin/users/" + admin.getId())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_OPERATION_FORBIDDEN"));
  }

  @Test
  void admin_lastAdminProtected_viaService() {
    // Інші класи без @Transactional могли залишити активних ADMIN у спільній H2.
    for (User leftover : userRepository.findActiveWithAvailableRole(Role.ADMIN)) {
      leftover.setActive(false);
      userRepository.save(leftover);
    }

    User soleAdmin = saveUser("sole-admin@example.com", "Admin123!", Role.ADMIN);
    String otherActorId = Objects.requireNonNull(UUID.randomUUID().toString());
    String soleAdminId = Objects.requireNonNull(soleAdmin.getId());

    assertThatThrownBy(
            () ->
                adminUserService.updateRole(
                    TestEnterprise.ID, otherActorId, soleAdminId, Role.MANAGER, null))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo("LAST_ADMIN_PROTECTED");

    assertThatThrownBy(
            () -> adminUserService.setActive(TestEnterprise.ID, otherActorId, soleAdminId, false))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo("LAST_ADMIN_PROTECTED");

    assertThatThrownBy(
            () -> adminUserService.softDelete(TestEnterprise.ID, otherActorId, soleAdminId))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo("LAST_ADMIN_PROTECTED");
  }

  @Test
  void admin_demoteAdmin_requiresSuperAdminPassword() throws Exception {
    User actor = saveUser("actor-demote@example.com", "Admin123!", Role.ADMIN);
    User targetAdmin = saveUser("target-demote@example.com", "Secret123", Role.ADMIN);
    Session session = login(actor.getEmail(), "Admin123!");

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + targetAdmin.getId() + "/role")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(toJson(new UpdateUserRoleRequest(Role.MANAGER, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPER_ADMIN_PASSWORD_REQUIRED"));

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + targetAdmin.getId() + "/role")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(toJson(new UpdateUserRoleRequest(Role.MANAGER, "wrong-password"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVALID_SUPER_ADMIN_PASSWORD"));

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + targetAdmin.getId() + "/role")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(
                    toJson(new UpdateUserRoleRequest(Role.MANAGER, "test-super-admin-password"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  void adminCanAssignMultipleRoles_andUserCanSwitch() throws Exception {
    User admin = saveUser("multi-admin-" + UUID.randomUUID() + "@ex.com", "Secret123", Role.ADMIN);
    User target = saveUser("multi-user-" + UUID.randomUUID() + "@ex.com", "Secret123", Role.USER);
    Session adminSession = login(admin.getEmail(), "Secret123");

    mockMvc
        .perform(
            put("/api/v1/admin/users/" + target.getId() + "/roles")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new UpdateUserRolesRequest(
                            Objects.requireNonNull(EnumSet.of(Role.USER, Role.MANAGER)), null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.availableRoles.length()").value(2))
        .andExpect(jsonPath("$.availableRoles[0]").value("USER"))
        .andExpect(jsonPath("$.availableRoles[1]").value("MANAGER"));

    Session userSession = login(target.getEmail(), "Secret123");
    mockMvc
        .perform(
            post("/api/v1/auth/switch-role")
                .header("Authorization", "Bearer " + userSession.access())
                .contentType(jsonContentType())
                .content(toJson(new SwitchRoleRequest(Role.MANAGER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.availableRoles.length()").value(2));

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + userSession.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"));

    mockMvc
        .perform(
            post("/api/v1/auth/switch-role")
                .header("Authorization", "Bearer " + userSession.access())
                .contentType(jsonContentType())
                .content(toJson(new SwitchRoleRequest(Role.ADMIN))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNED"));
  }

  @Test
  void filterByRoleMatchesAvailableNotOnlyActive() throws Exception {
    User admin = saveUser("filter-admin-" + UUID.randomUUID() + "@ex.com", "Secret123", Role.ADMIN);
    User target = saveUser("filter-user-" + UUID.randomUUID() + "@ex.com", "Secret123", Role.USER);
    target.replaceAvailableRoles(Objects.requireNonNull(EnumSet.of(Role.USER, Role.MANAGER)));
    target.setRole(Role.USER);
    userRepository.save(target);
    Session adminSession = login(admin.getEmail(), "Secret123");

    mockMvc
        .perform(
            get("/api/v1/admin/users")
                .param("role", "MANAGER")
                .param("email", target.getEmail())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(target.getId()));
  }

  private User saveUser(String email, String password, Role role) {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail(email);
    u.setPasswordHash(passwordEncoder.encode(password));
    u.setRole(role);
    u.setEmailVerified(true);
    u.setActive(true);
    return userRepository.save(u);
  }

  private Session login(String email, String password) throws Exception {
    MvcResult r =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(toJson(new LoginRequest(TestEnterprise.CODE, email, password))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode n = objectMapper.readTree(responseBody(r));
    return new Session(n.get("accessToken").asText(), n.get("refreshToken").asText());
  }

  @NonNull
  private MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(@NonNull Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  @NonNull
  private static String responseBody(@NonNull MvcResult result) {
    try {
      return Objects.requireNonNull(result.getResponse().getContentAsString());
    } catch (java.io.UnsupportedEncodingException ex) {
      throw new IllegalStateException("Cannot read response body", ex);
    }
  }

  private record Session(String access, String refresh) {}
}
