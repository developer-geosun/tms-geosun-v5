package com.geosun.tms.auth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.support.TestEnterprise;
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

/** Інтеграційні сценарії профілю користувача. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserProfileIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void getOwnProfile_withoutRow_returnsEmptyDto() throws Exception {
    User user = saveUser("profile-empty@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    mockMvc
        .perform(
            get("/api/v1/users/me/profile").header("Authorization", "Bearer " + session.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastName").doesNotExist())
        .andExpect(jsonPath("$.profileComplete").value(false))
        .andExpect(jsonPath("$.phones").isEmpty())
        .andExpect(jsonPath("$.preferredChannels").isEmpty());
  }

  @Test
  void putOwnProfile_individual_andMeReturnsDisplayName() throws Exception {
    User user = saveUser("profile-self@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    ObjectNode body = profileBody("Шевченко", "Тарас", "Григорович", "INDIVIDUAL", null);
    body.set("preferredChannels", channels("EMAIL", "MESSENGERS"));
    body.set("phones", phones(phone(null, "+380671112233", true, true, false, false)));

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastName").value("Шевченко"))
        .andExpect(jsonPath("$.profileComplete").value(true))
        .andExpect(jsonPath("$.phones[0].phone").value("+380671112233"))
        .andExpect(jsonPath("$.phones[0].primary").value(true));

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Шевченко Тарас Григорович"))
        .andExpect(jsonPath("$.profile.profileComplete").value(true));
  }

  @Test
  void put_rejectsEdrpouForIndividual() throws Exception {
    User user = saveUser("profile-edrpou-forbid@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    ObjectNode body = profileBody("Шевченко", "Тарас", null, "INDIVIDUAL", "14360570");
    body.set("preferredChannels", channels("EMAIL"));
    body.set("phones", objectMapper.createArrayNode());

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_EDRPOU_FORBIDDEN"));
  }

  @Test
  void put_legalEntityRequiresValidEdrpou() throws Exception {
    User user = saveUser("profile-legal@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    ObjectNode body =
        profileBody("Коваленко", "Олена", null, "LEGAL_ENTITY_REPRESENTATIVE", "14360570");
    body.set("preferredChannels", channels("EMAIL", "PHONE"));
    body.set("phones", phones(phone(null, "+380501112233", true, false, false, false)));

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legalEntityEdrpou").value("14360570"));
  }

  @Test
  void put_channelPhoneRequiresPhones() throws Exception {
    User user = saveUser("profile-phone-req@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    ObjectNode body = profileBody("Іваненко", "Іван", null, "INDIVIDUAL", null);
    body.set("preferredChannels", channels("PHONE"));
    body.set("phones", objectMapper.createArrayNode());

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_CHANNEL_PHONE_REQUIRED"));
  }

  @Test
  void put_duplicatePhoneRejected() throws Exception {
    User user = saveUser("profile-dup@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    ObjectNode body = profileBody("Петренко", "Петро", null, "INDIVIDUAL", null);
    body.set("preferredChannels", channels("EMAIL", "PHONE"));
    body.set(
        "phones",
        phones(
            phone(null, "+380671112233", true, false, false, false),
            phone(null, "+380 67-111-22-33", false, false, false, false)));

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROFILE_PHONE_DUPLICATE"));
  }

  @Test
  void rbac_userCannotReadOthers_managerReadOnly_adminCanWrite() throws Exception {
    User plain = saveUser("profile-rbac-user@example.com", "Secret123", Role.USER);
    User manager = saveUser("profile-rbac-mgr@example.com", "Secret123", Role.MANAGER);
    User admin = saveUser("profile-rbac-admin@example.com", "Admin123!", Role.ADMIN);
    User target = saveUser("profile-rbac-target@example.com", "Secret123", Role.USER);

    Session userSession = login(plain.getEmail(), "Secret123");
    Session managerSession = login(manager.getEmail(), "Secret123");
    Session adminSession = login(admin.getEmail(), "Admin123!");

    mockMvc
        .perform(
            get("/api/v1/admin/users/" + target.getId())
                .header("Authorization", "Bearer " + userSession.access()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/admin/users/" + target.getId())
                .header("Authorization", "Bearer " + managerSession.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value(target.getEmail()));

    ObjectNode body = profileBody("Адмін", "Редактор", null, "INDIVIDUAL", null);
    body.set("preferredChannels", channels("EMAIL"));
    body.set("phones", objectMapper.createArrayNode());

    mockMvc
        .perform(
            put("/api/v1/admin/users/" + target.getId() + "/profile")
                .header("Authorization", "Bearer " + managerSession.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/v1/admin/users/" + target.getId() + "/profile")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastName").value("Адмін"));
  }

  @Test
  void adminPut_deletedUser_returns409() throws Exception {
    User admin = saveUser("profile-del-admin@example.com", "Admin123!", Role.ADMIN);
    User target = saveUser("profile-del-target@example.com", "Secret123", Role.USER);
    target.setDeleted(true);
    target.setActive(false);
    userRepository.save(target);
    Session adminSession = login(admin.getEmail(), "Admin123!");

    ObjectNode body = profileBody("Видалений", "Користувач", null, "INDIVIDUAL", null);
    body.set("preferredChannels", channels("EMAIL"));
    body.set("phones", objectMapper.createArrayNode());

    mockMvc
        .perform(
            put("/api/v1/admin/users/" + target.getId() + "/profile")
                .header("Authorization", "Bearer " + adminSession.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_DELETED"));
  }

  @Test
  void put_replacesPhoneCollection() throws Exception {
    User user = saveUser("profile-replace@example.com", "Secret123", Role.USER);
    Session session = login(user.getEmail(), "Secret123");

    ObjectNode first = profileBody("Сидоренко", "Сидір", null, "INDIVIDUAL", null);
    first.set("preferredChannels", channels("EMAIL", "PHONE"));
    first.set(
        "phones",
        phones(
            phone(null, "+380671110001", true, false, false, false),
            phone(null, "+380671110002", false, false, false, false)));

    MvcResult saved =
        mockMvc
            .perform(
                put("/api/v1/users/me/profile")
                    .header("Authorization", "Bearer " + session.access())
                    .contentType(jsonContentType())
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(first))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phones.length()").value(2))
            .andReturn();

    String keepId =
        objectMapper
            .readTree(saved.getResponse().getContentAsString())
            .path("phones")
            .get(0)
            .path("id")
            .asText();

    ObjectNode second = profileBody("Сидоренко", "Сидір", null, "INDIVIDUAL", null);
    second.set("preferredChannels", channels("EMAIL", "PHONE"));
    second.set("phones", phones(phone(keepId, "+380671110001", true, true, false, false)));

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(second))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phones.length()").value(1))
        .andExpect(jsonPath("$.phones[0].telegram").value(true));
  }

  private ObjectNode profileBody(
      String lastName, String firstName, String patronymic, String personType, String edrpou) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("lastName", lastName);
    body.put("firstName", firstName);
    if (patronymic == null) {
      body.putNull("patronymic");
    } else {
      body.put("patronymic", patronymic);
    }
    body.put("personType", personType);
    if (edrpou == null) {
      body.putNull("legalEntityEdrpou");
    } else {
      body.put("legalEntityEdrpou", edrpou);
    }
    return body;
  }

  private ArrayNode channels(String... values) {
    ArrayNode arr = objectMapper.createArrayNode();
    for (String v : values) {
      arr.add(v);
    }
    return arr;
  }

  private ArrayNode phones(ObjectNode... items) {
    ArrayNode arr = objectMapper.createArrayNode();
    for (ObjectNode item : items) {
      arr.add(item);
    }
    return arr;
  }

  private ObjectNode phone(
      String id,
      String number,
      boolean primary,
      boolean telegram,
      boolean whatsapp,
      boolean viber) {
    ObjectNode node = objectMapper.createObjectNode();
    if (id != null) {
      node.put("id", id);
    }
    node.put("phone", number);
    node.put("primary", primary);
    node.put("telegram", telegram);
    node.put("whatsapp", whatsapp);
    node.put("viber", viber);
    return node;
  }

  private User saveUser(String email, String password, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setId(UUID.randomUUID().toString());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setActive(true);
    user.setDeleted(false);
    user.setEmailVerified(true);
    return userRepository.save(user);
  }

  private Session login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(
                        Objects.requireNonNull(
                            toJson(new LoginRequest(TestEnterprise.CODE, email, password)))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return new Session(json.get("accessToken").asText(), json.get("refreshToken").asText());
  }

  private @NonNull String toJson(@NonNull Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  private @NonNull MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  private record Session(String access, String refresh) {}
}
