package com.geosun.tms.reference.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.reference.api.ReferenceApiPaths;
import com.geosun.tms.reference.domain.VehicleType;
import com.geosun.tms.reference.dto.request.CreateVehicleRequest;
import com.geosun.tms.reference.dto.request.UpdateVehicleRequest;
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
class AdminVehicleIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void user_forbiddenOnVehicles() throws Exception {
    User user = saveUser("user-veh@example.com", "Secret123", Role.USER);
    String token = login(Objects.requireNonNull(user.getEmail()), "Secret123");
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void manager_crudSoftDeleteRestoreAndScans() throws Exception {
    User manager = saveUser("manager-veh@example.com", "Secret123", Role.MANAGER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    CreateVehicleRequest create =
        new CreateVehicleRequest(
            "AA1234BB",
            "WVWZZZ1JZYW000001",
            "Volvo",
            "FH16",
            (short) 2020,
            "ТОВ Тест",
            "АВС",
            "123456",
            VehicleType.SEMI_TRACTOR,
            false);

    MvcResult createdResult =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(jsonContentType())
                    .content(toJson(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plateNumber").value("AA1234BB"))
            .andExpect(jsonPath("$.vin").value("WVWZZZ1JZYW000001"))
            .andExpect(jsonPath("$.deleted").value(false))
            .andExpect(jsonPath("$.hasRefrigerator").value(false))
            .andExpect(jsonPath("$.documentCompliance").value("PROBLEM"))
            .andReturn();

    String id =
        objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(jsonContentType())
                .content(toJson(create)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PLATE_ALREADY_EXISTS"));

    MockMultipartFile front = new MockMultipartFile("file", "front.jpg", "image/jpeg", jpegBytes());
    mockMvc
        .perform(
            multipart(
                    ReferenceApiPaths.ADMIN_VEHICLES_BASE
                        + "/"
                        + id
                        + "/registration-certificate/front")
                .file(front)
                .with(
                    request -> {
                      request.setMethod("PUT");
                      return request;
                    })
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalFilename").value("front.jpg"));

    MvcResult download =
        mockMvc
            .perform(
                get(ReferenceApiPaths.ADMIN_VEHICLES_BASE
                        + "/"
                        + id
                        + "/registration-certificate/front")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(download.getResponse().getContentAsByteArray()).isNotEmpty();

    mockMvc
        .perform(
            delete(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                .param("view", "active")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + id + "/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));

    UpdateVehicleRequest update =
        new UpdateVehicleRequest(
            "AA1234BB",
            "WVWZZZ1JZYW000001",
            "Volvo",
            "FH16",
            (short) 2021,
            "ТОВ Тест",
            "АВС",
            "123456",
            VehicleType.SEMI_TRACTOR,
            false);
    mockMvc
        .perform(
            put(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(jsonContentType())
                .content(toJson(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manufactureYear").value(2021));
  }

  @Test
  void manager_rejectsInvalidPlate_andNormalizesSpacedCyrillicPlate() throws Exception {
    User manager = saveUser("manager-veh-plate@example.com", "Secret123", Role.MANAGER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    CreateVehicleRequest invalid =
        new CreateVehicleRequest(
            "AA12",
            "WVWZZZ1JZYW000002",
            "Volvo",
            "FH16",
            (short) 2020,
            "ТОВ Тест",
            "АВС",
            "654321",
            VehicleType.SEMI_TRACTOR,
            false);
    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(jsonContentType())
                .content(toJson(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    CreateVehicleRequest spacedCyrillic =
        new CreateVehicleRequest(
            "АА 1234 ВВ",
            "WVWZZZ1JZYW000003",
            "Volvo",
            "FH16",
            (short) 2020,
            "ТОВ Тест",
            "АВС",
            "654322",
            VehicleType.SEMI_TRACTOR,
            false);
    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(jsonContentType())
                .content(toJson(spacedCyrillic)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.plateNumber").value("AA1234BB"));
  }

  @Test
  void manager_rejectsInvalidVin_andNormalizesSpacedVin() throws Exception {
    User manager = saveUser("manager-veh-vin@example.com", "Secret123", Role.MANAGER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    CreateVehicleRequest invalid =
        new CreateVehicleRequest(
            "BC1234DE",
            "SHORT",
            "Volvo",
            "FH16",
            (short) 2020,
            "ТОВ Тест",
            "АВС",
            "111111",
            VehicleType.SEMI_TRACTOR,
            false);
    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(jsonContentType())
                .content(toJson(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    CreateVehicleRequest spaced =
        new CreateVehicleRequest(
            "BC1234EF",
            "WVW ZZZ1JZY W000004",
            "Volvo",
            "FH16",
            (short) 2020,
            "ТОВ Тест",
            "АВС",
            "111112",
            VehicleType.SEMI_TRACTOR,
            false);
    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(jsonContentType())
                .content(toJson(spaced)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vin").value("WVWZZZ1JZYW000004"));
  }

  @Test
  void manager_vehicleDocuments_historyAndCompliance() throws Exception {
    User manager = saveUser("manager-veh-docs@example.com", "Secret123", Role.MANAGER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    CreateVehicleRequest create =
        new CreateVehicleRequest(
            "CA1234DE",
            "WVWZZZ1JZYW000010",
            "MAN",
            "TGX",
            (short) 2022,
            "ТОВ Тест",
            "XYZ",
            "900001",
            VehicleType.SEMI_TRACTOR,
            false);

    MvcResult createdResult =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(jsonContentType())
                    .content(toJson(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentCompliance").value("PROBLEM"))
            .andReturn();
    String id =
        objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + id + "/documents")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.length()").value(5))
        .andExpect(jsonPath("$.documents[0].status").value("MISSING"));

    MockMultipartFile scan1 =
        new MockMultipartFile("file", "liability-v1.jpg", "image/jpeg", jpegBytes());
    MvcResult v1 =
        mockMvc
            .perform(
                multipart(
                        ReferenceApiPaths.ADMIN_VEHICLES_BASE
                            + "/"
                            + id
                            + "/documents/THIRD_PARTY_LIABILITY")
                    .file(scan1)
                    .param("validFrom", "2025-01-01")
                    .param("validTo", "2025-12-31")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andReturn();
    String v1Id = objectMapper.readTree(v1.getResponse().getContentAsString()).get("id").asText();

    MockMultipartFile scan2 =
        new MockMultipartFile("file", "liability-v2.jpg", "image/jpeg", jpegBytes());
    MvcResult v2 =
        mockMvc
            .perform(
                multipart(
                        ReferenceApiPaths.ADMIN_VEHICLES_BASE
                            + "/"
                            + id
                            + "/documents/THIRD_PARTY_LIABILITY")
                    .file(scan2)
                    .param("validFrom", "2026-01-01")
                    .param("validTo", "2027-12-31")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("VALID"))
            .andReturn();
    String v2Id = objectMapper.readTree(v2.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + id + "/documents")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents[0].documentType").value("THIRD_PARTY_LIABILITY"))
        .andExpect(jsonPath("$.documents[0].current.id").value(v2Id))
        .andExpect(jsonPath("$.documents[0].history.length()").value(1))
        .andExpect(jsonPath("$.documents[0].history[0].id").value(v1Id));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + id + "/documents/" + v1Id + "/scan")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // Рефрижератор недоступний для тягача
    MockMultipartFile fridge =
        new MockMultipartFile("file", "fridge.jpg", "image/jpeg", jpegBytes());
    mockMvc
        .perform(
            multipart(
                    ReferenceApiPaths.ADMIN_VEHICLES_BASE
                        + "/"
                        + id
                        + "/documents/REFRIGERATOR_VERIFICATION")
                .file(fridge)
                .param("validFrom", "2026-01-01")
                .param("validTo", "2027-01-01")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_ALLOWED"));

    CreateVehicleRequest trailer =
        new CreateVehicleRequest(
            "CB1234EF",
            "WVWZZZ1JZYW000011",
            "SCHMITZ",
            "SKO",
            (short) 2021,
            "ТОВ Тест",
            "XYZ",
            "900002",
            VehicleType.SEMI_TRAILER,
            true);
    MvcResult trailerResult =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(jsonContentType())
                    .content(toJson(trailer)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.hasRefrigerator").value(true))
            .andReturn();
    String trailerId =
        objectMapper.readTree(trailerResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + trailerId + "/documents")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.length()").value(4));
  }

  @NonNull
  private static MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(@NonNull Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  private static byte[] jpegBytes() {
    // Мінімальний JPEG SOI/EOI достатній для content-type перевірки
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
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

  private String login(@NonNull String email, @NonNull String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(toJson(new LoginRequest(TestEnterprise.CODE, email, password))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode n = objectMapper.readTree(result.getResponse().getContentAsString());
    return n.get("accessToken").asText();
  }
}
