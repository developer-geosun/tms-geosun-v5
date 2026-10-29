package com.geosun.tms.storage.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.storage.api.StorageApiPaths;
import com.geosun.tms.support.TestEnterprise;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminStoredFileIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void managerAndUser_forbiddenOnStoredFiles() throws Exception {
    User manager = saveUser("manager-files@example.com", "Secret123", Role.MANAGER);
    User plain = saveUser("user-files@example.com", "Secret123", Role.USER);
    String managerToken = login(Objects.requireNonNull(manager.getEmail()), "Secret123");
    String userToken = login(Objects.requireNonNull(plain.getEmail()), "Secret123");

    mockMvc
        .perform(
            get(StorageApiPaths.ADMIN_STORED_FILES_BASE)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get(StorageApiPaths.ADMIN_STORED_FILES_BASE)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void admin_uploadListDownloadDelete() throws Exception {
    User admin = saveUser("admin-files@example.com", "Admin123!", Role.ADMIN);
    String token = login(Objects.requireNonNull(admin.getEmail()), "Admin123!");

    mockMvc
        .perform(
            get(StorageApiPaths.ADMIN_STORED_FILES_BASE + "/storage-info")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("local"));

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "hello.txt",
            "text/plain",
            "storage-it-payload".getBytes(StandardCharsets.UTF_8));

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart(StorageApiPaths.ADMIN_STORED_FILES_BASE)
                    .file(file)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originalFilename").value("hello.txt"))
            .andExpect(
                jsonPath("$.storageKey")
                    .value(Objects.requireNonNull(org.hamcrest.Matchers.startsWith("admin-test/"))))
            .andReturn();

    JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
    String id = uploaded.get("id").asText();

    mockMvc
        .perform(
            get(StorageApiPaths.ADMIN_STORED_FILES_BASE).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));

    MvcResult download =
        mockMvc
            .perform(
                get(StorageApiPaths.ADMIN_STORED_FILES_BASE + "/" + id)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(download.getResponse().getContentAsString()).isEqualTo("storage-it-payload");

    mockMvc
        .perform(
            delete(StorageApiPaths.ADMIN_STORED_FILES_BASE + "/" + id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get(StorageApiPaths.ADMIN_STORED_FILES_BASE + "/" + id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  private User saveUser(String email, String rawPassword, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setRole(role);
    user.setActive(true);
    user.setEmailVerified(true);
    return userRepository.save(user);
  }

  private String login(@NonNull String email, @NonNull String password) throws Exception {
    LoginRequest body = new LoginRequest(TestEnterprise.CODE, email, password);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }
}
