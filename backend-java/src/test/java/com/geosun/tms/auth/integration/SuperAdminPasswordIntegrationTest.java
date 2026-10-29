package com.geosun.tms.auth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.VerifySuperAdminPasswordRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.support.TestEnterprise;
import java.util.Objects;
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
 * Інтеграційні сценарії перевірки пароля суперадміна.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuperAdminPasswordIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void admin_correctPassword_ok() throws Exception {
    User admin = saveUser("sa-admin-ok@example.com", "Admin123!", Role.ADMIN);
    Session session = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            post("/api/v1/admin/super-admin/verify-password")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(toJson(new VerifySuperAdminPasswordRequest("test-super-admin-password"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void admin_wrongPassword_forbidden() throws Exception {
    User admin = saveUser("sa-admin-bad@example.com", "Admin123!", Role.ADMIN);
    Session session = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            post("/api/v1/admin/super-admin/verify-password")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(toJson(new VerifySuperAdminPasswordRequest("wrong-password"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVALID_SUPER_ADMIN_PASSWORD"));
  }

  @Test
  void manager_forbidden() throws Exception {
    User manager = saveUser("sa-manager@example.com", "Secret123", Role.MANAGER);
    Session session = login(manager.getEmail(), "Secret123");

    mockMvc
        .perform(
            post("/api/v1/admin/super-admin/verify-password")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(toJson(new VerifySuperAdminPasswordRequest("test-super-admin-password"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void unauthenticated_unauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/super-admin/verify-password")
                .contentType(jsonContentType())
                .content(toJson(new VerifySuperAdminPasswordRequest("test-super-admin-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
