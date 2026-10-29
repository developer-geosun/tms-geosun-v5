package com.geosun.tms.reference.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.reference.domain.CountryReference;
import com.geosun.tms.reference.repository.CountryReferenceRepository;
import com.geosun.tms.support.TestEnterprise;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CountryReferenceApiIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CountryReferenceRepository countryReferenceRepository;

  @BeforeEach
  void seedCountries() {
    countryReferenceRepository.deleteAll();
    saveCountry("UA", "UKR", "Україна", "Ukraine", "Украина");
    saveCountry("PL", "POL", "Польща", "Poland", "Польша");
    saveCountry("DE", "DEU", "Німеччина", "Germany", "Германия");
  }

  @Test
  void adminCanListSearchAndGetCountryByCode() throws Exception {
    User admin = createUser("country-ref-admin@example.com", Role.ADMIN);
    String access = login(admin.getEmail());

    mockMvc
        .perform(get("/api/v1/admin/country-reference").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.codeAlpha2=='UA')]").exists())
        .andExpect(jsonPath("$[?(@.codeAlpha2=='DE')]").exists());

    mockMvc
        .perform(
            get("/api/v1/admin/country-reference")
                .header("Authorization", bearer(access))
                .param("search", "pol"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codeAlpha2").value("PL"));

    mockMvc
        .perform(get("/api/v1/admin/country-reference/pl").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeAlpha2").value("PL"))
        .andExpect(jsonPath("$.codeAlpha3").value("POL"));
  }

  @Test
  void userCannotAccessCountryReference() throws Exception {
    User user = createUser("country-ref-user@example.com", Role.USER);
    String access = login(user.getEmail());

    mockMvc
        .perform(get("/api/v1/admin/country-reference").header("Authorization", bearer(access)))
        .andExpect(status().isForbidden());
  }

  private void saveCountry(
      String alpha2, String alpha3, String nameUk, String nameEn, String nameRu) {
    CountryReference country = new CountryReference();
    country.setCodeAlpha2(alpha2);
    country.setCodeAlpha3(alpha3);
    country.setNameUk(nameUk);
    country.setNameEn(nameEn);
    country.setNameRu(nameRu);
    countryReferenceRepository.save(country);
  }

  private User createUser(String email, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("Secret123"));
    user.setRole(role);
    user.setActive(true);
    user.setEmailVerified(true);
    return userRepository.save(user);
  }

  private String login(String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(
                        Objects.requireNonNull(
                            toJson(new LoginRequest(TestEnterprise.CODE, email, "Secret123")))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  @NonNull
  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private String toJson(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
