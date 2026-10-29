package com.geosun.tms.reference.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.geosun.tms.reference.domain.CountryReference;
import com.geosun.tms.reference.domain.DocumentTypeFieldDefinition;
import com.geosun.tms.reference.domain.DocumentTypeReference;
import com.geosun.tms.reference.domain.DocumentTypeScanPage;
import com.geosun.tms.reference.dto.request.CreateDocumentTypeRequest;
import com.geosun.tms.reference.dto.request.DocumentTypeFieldDefinitionRequest;
import com.geosun.tms.reference.dto.request.DocumentTypeScanPageRequest;
import com.geosun.tms.reference.dto.request.UpdateDocumentTypeRequest;
import com.geosun.tms.reference.repository.CountryReferenceRepository;
import com.geosun.tms.reference.repository.DocumentTypeReferenceRepository;
import com.geosun.tms.support.TestEnterprise;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDocumentTypeIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CountryReferenceRepository countryReferenceRepository;
  @Autowired private DocumentTypeReferenceRepository documentTypeRepository;

  @MockBean private JavaMailSender javaMailSender;

  @BeforeEach
  void seedCountries() {
    countryReferenceRepository.deleteAll();
    documentTypeRepository.deleteAll();
    saveCountry("UA", "UKR", "Україна", "Ukraine", "Украина");
    saveCountry("PL", "POL", "Польща", "Poland", "Польша");
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

  @Test
  void user_forbiddenOnDocumentTypes() throws Exception {
    User user = saveUser("user-doctype@example.com", "Secret123", Role.USER);
    String token = login(Objects.requireNonNull(user.getEmail()), "Secret123");
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void manager_forbiddenOnDocumentTypes() throws Exception {
    User manager = saveUser("manager-doctype@example.com", "Secret123", Role.MANAGER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void admin_documentTypeCrudRestoreAndValidation() throws Exception {
    User admin = saveUser("admin-doctype@example.com", "Secret123", Role.ADMIN);
    String token = login(Objects.requireNonNull(admin.getEmail()), "Secret123");

    CreateDocumentTypeRequest create =
        new CreateDocumentTypeRequest(
            "Паспорт тест",
            "Passport test",
            "Паспорт тест",
            "UA",
            frontBackPages(),
            "MRZ test",
            List.of(
                new DocumentTypeFieldDefinitionRequest(
                    "lastName", "Прізвище", "Last name", "Фамилия", true)));

    MvcResult created =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(json())
                    .content(Objects.requireNonNull(toJson(create))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nameUk").value("Паспорт тест"))
            .andExpect(jsonPath("$.countryCode").value("UA"))
            .andExpect(jsonPath("$.plannedScanPages.length()").value(2))
            .andExpect(jsonPath("$.plannedScanPages[0].key").value("1"))
            .andExpect(jsonPath("$.comment").value("MRZ test"))
            .andExpect(jsonPath("$.fieldDefinitions.length()").value(1))
            .andExpect(jsonPath("$.fieldDefinitions[0].key").value("lastName"))
            .andExpect(jsonPath("$.fieldDefinitions[0].required").value(true))
            .andReturn();
    String id =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(create))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NAME_EXISTS"));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .param("view", "active")
                .param("country", "UA")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    UpdateDocumentTypeRequest update =
        new UpdateDocumentTypeRequest(
            "Паспорт громадянина тест",
            "Citizen passport test",
            "Паспорт гражданина тест",
            "UA",
            List.of(),
            "",
            List.of(
                new DocumentTypeFieldDefinitionRequest(
                    "lastName", "Прізвище", "Last name", "Фамилия", true),
                new DocumentTypeFieldDefinitionRequest(
                    "firstName", "Ім'я", "First name", "Имя", false)));

    mockMvc
        .perform(
            put(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(update))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nameUk").value("Паспорт громадянина тест"))
        .andExpect(jsonPath("$.plannedScanPages.length()").value(0))
        .andExpect(jsonPath("$.comment").value(""))
        .andExpect(jsonPath("$.fieldDefinitions.length()").value(2));

    mockMvc
        .perform(
            delete(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE + "/" + id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .param("view", "deleted")
                .param("search", "громадянина тест")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE + "/" + id + "/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));

    CreateDocumentTypeRequest invalidCountry =
        new CreateDocumentTypeRequest(
            "Test",
            "Test",
            "Test",
            "ZZ",
            List.of(),
            "",
            List.of(new DocumentTypeFieldDefinitionRequest("code", "Код", "Code", "Код", true)));

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(invalidCountry))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COUNTRY_NOT_FOUND"));

    CreateDocumentTypeRequest invalidFieldKey =
        new CreateDocumentTypeRequest(
            "Інший ключ",
            "Other key",
            "Другой ключ",
            "UA",
            List.of(),
            "",
            List.of(new DocumentTypeFieldDefinitionRequest("1bad", "A", "B", "C", true)));

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(invalidFieldKey))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    CreateDocumentTypeRequest emptyFields =
        new CreateDocumentTypeRequest(
            "Порожні поля", "Empty fields", "Пустые поля", "UA", List.of(), "", List.of());

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(emptyFields))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_FIELDS_EMPTY"));

    CreateDocumentTypeRequest noRequired =
        new CreateDocumentTypeRequest(
            "Без обовʼязкових",
            "No required",
            "Без обязательных",
            "UA",
            List.of(),
            "",
            List.of(
                new DocumentTypeFieldDefinitionRequest(
                    "note", "Примітка", "Note", "Примечание", false)));

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(noRequired))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NO_REQUIRED_FIELD"));

    CreateDocumentTypeRequest duplicatePageKey =
        new CreateDocumentTypeRequest(
            "Дубль сторінки",
            "Dup page",
            "Дубль страницы",
            "UA",
            List.of(
                new DocumentTypeScanPageRequest("1", "front", "лицьова", "лицевая"),
                new DocumentTypeScanPageRequest("1", "back", "зворотна", "обратная")),
            "",
            List.of(new DocumentTypeFieldDefinitionRequest("code", "Код", "Code", "Код", true)));

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(Objects.requireNonNull(toJson(duplicatePageKey))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_SCAN_PAGE_KEY_DUPLICATE"));
  }

  @Test
  void admin_uaDefaultCatalogSeed_hasFiveTypesWithExpectedMrz() throws Exception {
    // У test-профілі Flyway вимкнено — сіємо каталог як у V39 через репозиторій.
    seedUaDefaultCatalog();

    User admin = saveUser("admin-seed-doctype@example.com", "Secret123", Role.ADMIN);
    String token = login(Objects.requireNonNull(admin.getEmail()), "Secret123");

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE)
                .param("view", "active")
                .param("country", "UA")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE
                    + "/c2000000-0000-4000-8000-000000000001")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plannedScanPages.length()").value(0))
        .andExpect(jsonPath("$.fieldDefinitions.length()").value(8))
        .andExpect(jsonPath("$.comment").value("немає ICAO MRZ (книжковий внутрішній паспорт)"));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE
                    + "/c2000000-0000-4000-8000-000000000002")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plannedScanPages.length()").value(2))
        .andExpect(jsonPath("$.plannedScanPages[0].key").value("1"))
        .andExpect(jsonPath("$.plannedScanPages[1].key").value("2"))
        .andExpect(jsonPath("$.comment").value("ICAO 9303 TD1 (3×30)"))
        .andExpect(jsonPath("$.fieldDefinitions.length()").value(8));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE
                    + "/c2000000-0000-4000-8000-000000000003")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comment").value("ICAO 9303 TD3 (2×44)"))
        .andExpect(jsonPath("$.fieldDefinitions.length()").value(6));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE
                    + "/c2000000-0000-4000-8000-000000000004")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comment").value("немає ICAO MRZ"))
        .andExpect(jsonPath("$.fieldDefinitions.length()").value(6));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DOCUMENT_TYPES_BASE
                    + "/c2000000-0000-4000-8000-000000000005")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comment").value("немає ICAO MRZ"))
        .andExpect(jsonPath("$.fieldDefinitions.length()").value(5))
        .andExpect(jsonPath("$.fieldDefinitions[0].required").value(true))
        .andExpect(jsonPath("$.fieldDefinitions[4].required").value(false));
  }

  private void seedUaDefaultCatalog() {
    List<DocumentTypeScanPage> frontBack =
        List.of(
            new DocumentTypeScanPage("1", "front", "лицьова сторона", "лицевая сторона"),
            new DocumentTypeScanPage("2", "back", "зворотна сторона", "обратная сторона"));

    saveCatalogRow(
        "c2000000-0000-4000-8000-000000000001",
        "Паспорт громадянина України (книжковий)",
        "National passport (booklet)",
        "Национальный паспорт (книжка)",
        List.of(),
        "немає ICAO MRZ (книжковий внутрішній паспорт)",
        List.of(
            field("passportSeries", "Серія", "Series", "Серия", true),
            field("passportNumber", "Номер", "Number", "Номер", true),
            field("lastName", "Прізвище", "Last name", "Фамилия", true),
            field("firstName", "Ім'я", "First name", "Имя", true),
            field("patronymic", "По батькові", "Patronymic", "Отчество", false),
            field("issuedOn", "Дата видачі", "Issue date", "Дата выдачи", true),
            field("issuedBy", "Ким виданий", "Issued by", "Кем выдан", true),
            field("expiresOn", "Дійсний до", "Valid until", "Действителен до", false)));

    saveCatalogRow(
        "c2000000-0000-4000-8000-000000000002",
        "ID-картка",
        "ID card",
        "ID-карточка",
        frontBack,
        "ICAO 9303 TD1 (3×30)",
        List.of(
            field("documentNumber", "Номер документа", "Document number", "Номер документа", true),
            field("recordNumber", "Запис № (УНЗР)", "Record No.", "Запись №", true),
            field("lastName", "Прізвище", "Last name", "Фамилия", true),
            field("firstName", "Ім'я", "First name", "Имя", true),
            field("patronymic", "По батькові", "Patronymic", "Отчество", false),
            field("issuedOn", "Дата видачі", "Issue date", "Дата выдачи", true),
            field("expiresOn", "Дійсний до", "Valid until", "Действителен до", true),
            field("issuedBy", "Ким виданий", "Issued by", "Кем выдан", true)));

    saveCatalogRow(
        "c2000000-0000-4000-8000-000000000003",
        "Закордонний паспорт",
        "International passport",
        "Заграничный паспорт",
        frontBack,
        "ICAO 9303 TD3 (2×44)",
        List.of(
            field("passportNumber", "Номер паспорта", "Passport number", "Номер паспорта", true),
            field("lastName", "Прізвище", "Last name", "Фамилия", true),
            field("firstName", "Ім'я", "First name", "Имя", true),
            field("issuedOn", "Дата видачі", "Issue date", "Дата выдачи", true),
            field("expiresOn", "Дійсний до", "Valid until", "Действителен до", true),
            field("issuedBy", "Ким виданий", "Issued by", "Кем выдан", true)));

    saveCatalogRow(
        "c2000000-0000-4000-8000-000000000004",
        "Посвідчення водія",
        "Driver's license",
        "Водительское удостоверение",
        frontBack,
        "немає ICAO MRZ",
        List.of(
            field(
                "licenseNumber",
                "Номер посвідчення",
                "License number",
                "Номер удостоверения",
                true),
            field("licenseCategories", "Категорії", "Categories", "Категории", true),
            field("licenseIssuedOn", "Дата видачі", "Issue date", "Дата выдачи", true),
            field("licenseExpiresOn", "Дійсне до", "Valid until", "Действует до", true),
            field("lastName", "Прізвище", "Last name", "Фамилия", true),
            field("firstName", "Ім'я", "First name", "Имя", true)));

    saveCatalogRow(
        "c2000000-0000-4000-8000-000000000005",
        "Свідоцтво про реєстрацію ТЗ",
        "Vehicle registration certificate",
        "Свидетельство о регистрации ТС",
        frontBack,
        "немає ICAO MRZ",
        List.of(
            field(
                "registrationSeries",
                "Серія свідоцтва",
                "Registration series",
                "Серия свидетельства",
                true),
            field(
                "registrationNumber",
                "Номер свідоцтва",
                "Registration number",
                "Номер свидетельства",
                true),
            field("vin", "VIN", "VIN", "VIN", true),
            field(
                "plateNumber",
                "Реєстраційний номер",
                "Plate number",
                "Регистрационный номер",
                true),
            field("issuedOn", "Дата видачі", "Issue date", "Дата выдачи", false)));
  }

  private void saveCatalogRow(
      String id,
      String nameUk,
      String nameEn,
      String nameRu,
      List<DocumentTypeScanPage> pages,
      String comment,
      List<DocumentTypeFieldDefinition> fields) {
    DocumentTypeReference row = new DocumentTypeReference();
    row.setId(id);
    row.setEnterpriseId(TestEnterprise.ID);
    row.setNameUk(nameUk);
    row.setNameEn(nameEn);
    row.setNameRu(nameRu);
    row.setCountryCode("UA");
    row.setPlannedScanPages(pages);
    row.setComment(comment);
    row.setFieldDefinitions(fields);
    row.setDeleted(false);
    documentTypeRepository.save(Objects.requireNonNull(row));
  }

  private static DocumentTypeFieldDefinition field(
      String key, String nameUk, String nameEn, String nameRu, boolean required) {
    return new DocumentTypeFieldDefinition(key, nameUk, nameEn, nameRu, required);
  }

  private List<DocumentTypeScanPageRequest> frontBackPages() {
    return List.of(
        new DocumentTypeScanPageRequest("1", "front", "лицьова сторона", "лицевая сторона"),
        new DocumentTypeScanPageRequest("2", "back", "зворотна сторона", "обратная сторона"));
  }

  private User saveUser(String email, String password, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setActive(true);
    user.setEmailVerified(true);
    return userRepository.save(user);
  }

  private String login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(json())
                    .content(
                        Objects.requireNonNull(
                            toJson(new LoginRequest(TestEnterprise.CODE, email, password)))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("accessToken").asText();
  }

  @NonNull
  private MediaType json() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  private String toJson(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
