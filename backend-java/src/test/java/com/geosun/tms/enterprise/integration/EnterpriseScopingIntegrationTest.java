package com.geosun.tms.enterprise.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.enterprise.dto.CreateEnterpriseRequest;
import com.geosun.tms.enterprise.dto.EnterpriseDto;
import com.geosun.tms.enterprise.service.PlatformEnterpriseService;
import com.geosun.tms.reference.api.ReferenceApiPaths;
import com.geosun.tms.reference.domain.DocumentTypeReference;
import com.geosun.tms.reference.domain.Vehicle;
import com.geosun.tms.reference.domain.VehicleType;
import com.geosun.tms.reference.repository.DocumentTypeReferenceRepository;
import com.geosun.tms.reference.repository.VehicleRepository;
import com.geosun.tms.support.TestEnterprise;
import com.geosun.tms.trips.domain.TripNumberSeq;
import com.geosun.tms.trips.domain.TripNumberSeqId;
import com.geosun.tms.trips.repository.TripNumberSeqRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Ізоляція операційних даних між підприємствами (крок 2.3). */
@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnterpriseScopingIntegrationTest {

  private static final String PASSWORD = "Secret123";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PlatformEnterpriseService platformEnterpriseService;
  @Autowired private DocumentTypeReferenceRepository documentTypeRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private TripNumberSeqRepository tripNumberSeqRepository;

  @Test
  void createEnterprise_copiesDocumentTypesWithNewIds_andIsolatesThem() throws Exception {
    DocumentTypeReference source = saveDocumentType(TestEnterprise.ID, "Паспорт-тест");

    EnterpriseDto created =
        platformEnterpriseService.create(
            new CreateEnterpriseRequest(
                "Scoped", "scoped-ent", "admin@scoped.example.com", PASSWORD));
    String newEnterpriseId = Objects.requireNonNull(created.id());

    List<DocumentTypeReference> copies =
        documentTypeRepository.findByEnterpriseIdAndDeletedFalseOrderByCountryCodeAscNameUkAsc(
            newEnterpriseId);
    assertThat(copies).isNotEmpty();
    assertThat(copies).allSatisfy(copy -> assertThat(copy.getId()).isNotEqualTo(source.getId()));
    assertThat(copies).anySatisfy(copy -> assertThat(copy.getNameUk()).isEqualTo("Паспорт-тест"));

    String access = login("scoped-ent", "admin@scoped.example.com");
    // Чужий id — 404, як і неіснуючий
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE + "/" + source.getId())
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE + "/" + copies.get(0).getId())
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
  }

  @Test
  void vehicles_areScoped_listAndGetById() throws Exception {
    Vehicle foreign = saveVehicle(TestEnterprise.ID, "AA0001AA");

    platformEnterpriseService.create(
        new CreateEnterpriseRequest("Veh", "veh-ent", "admin@veh.example.com", PASSWORD));
    String access = login("veh-ent", "admin@veh.example.com");

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE).header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_VEHICLES_BASE + "/" + foreign.getId())
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isNotFound());
  }

  @Test
  void tripNumberSequence_isKeyedByEnterpriseAndYear() {
    EnterpriseDto other =
        platformEnterpriseService.create(
            new CreateEnterpriseRequest("Seq", "seq-ent", "admin@seq.example.com", PASSWORD));
    String otherId = Objects.requireNonNull(other.id());

    saveSeq(TestEnterprise.ID, 2026, 7);
    saveSeq(otherId, 2026, 1);

    TripNumberSeq first =
        tripNumberSeqRepository
            .findById(new TripNumberSeqId(TestEnterprise.ID, 2026))
            .orElseThrow();
    TripNumberSeq second =
        tripNumberSeqRepository.findById(new TripNumberSeqId(otherId, 2026)).orElseThrow();
    assertThat(first.getLastValue()).isEqualTo(7);
    assertThat(second.getLastValue()).isEqualTo(1);
    assertThat(
            tripNumberSeqRepository
                .findByEnterpriseIdAndYearForUpdate(otherId, 2026)
                .orElseThrow()
                .getLastValue())
        .isEqualTo(1);
  }

  private void saveSeq(String enterpriseId, int year, int lastValue) {
    TripNumberSeq seq = new TripNumberSeq();
    seq.setEnterpriseId(enterpriseId);
    seq.setYear(year);
    seq.setLastValue(lastValue);
    tripNumberSeqRepository.saveAndFlush(Objects.requireNonNull(seq));
  }

  private DocumentTypeReference saveDocumentType(String enterpriseId, String nameUk) {
    DocumentTypeReference row = new DocumentTypeReference();
    row.setId(UUID.randomUUID().toString());
    row.setEnterpriseId(enterpriseId);
    row.setNameUk(nameUk);
    row.setNameEn("Passport test");
    row.setNameRu("Паспорт-тест");
    row.setCountryCode("UA");
    row.setPlannedScanPages(List.of());
    row.setFieldDefinitions(List.of());
    row.setDeleted(false);
    return documentTypeRepository.saveAndFlush(Objects.requireNonNull(row));
  }

  private Vehicle saveVehicle(String enterpriseId, String plate) {
    Vehicle vehicle = new Vehicle();
    vehicle.setId(UUID.randomUUID().toString());
    vehicle.setEnterpriseId(enterpriseId);
    vehicle.setPlateNumber(plate);
    vehicle.setVin("WDB00000000000001");
    vehicle.setMake("Make");
    vehicle.setModel("Model");
    vehicle.setManufactureYear((short) 2020);
    vehicle.setOwner("Owner");
    vehicle.setRegistrationSeries("AAA");
    vehicle.setRegistrationNumber("123456");
    vehicle.setVehicleType(VehicleType.SEMI_TRACTOR);
    vehicle.setDeleted(false);
    return vehicleRepository.saveAndFlush(Objects.requireNonNull(vehicle));
  }

  private String login(String enterpriseCode, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonType())
                    .content(
                        Objects.requireNonNull(
                            objectMapper.writeValueAsString(
                                new LoginRequest(enterpriseCode, email, PASSWORD)))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  @NonNull
  private static MediaType jsonType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }
}
