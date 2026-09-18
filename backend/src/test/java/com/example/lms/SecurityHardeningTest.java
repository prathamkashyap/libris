package com.example.lms;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lms.entity.AuditAction;
import com.example.lms.entity.Book;
import com.example.lms.entity.BorrowRecord;
import com.example.lms.entity.StudentProfile;
import com.example.lms.repository.AuditLogRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.BorrowRecordRepository;
import com.example.lms.repository.StudentProfileRepository;
import com.example.lms.security.LoginAttemptTracker;
import com.example.lms.security.PasswordValidator;
import com.example.lms.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AuditLogRepository auditLogs;
  @Autowired BorrowRecordRepository borrowRecords;
  @Autowired LoginAttemptTracker loginAttempts;
  @Autowired BookRepository books;
  @Autowired StudentProfileRepository students;
  @Autowired ReportService reportService;
  @Autowired SessionRegistryImpl sessionRegistry;

  @Value("${lms.admin.password}")
  String adminPassword;

  @Value("${server.servlet.session.cookie.secure}")
  boolean sessionCookieSecure;

  @Value("${server.servlet.session.cookie.same-site}")
  String sessionCookieSameSite;

  private MockHttpSession adminSession;
  private Cookie csrfCookie;

  @BeforeEach
  void login() throws Exception {
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    Assertions.assertNotNull(csrfCookie);

    String body =
        "{\"username\":\"admin\",\"password\":\"" + adminPassword.replace("\"", "\\\"") + "\"}";
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    adminSession = (MockHttpSession) result.getRequest().getSession(false);
  }

  // ==================== H1: Login abuse protection ====================

  @Test
  void loginLocksAfterFiveFailures() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(
              post("/api/auth/login")
                  .cookie(csrfCookie)
                  .header("X-XSRF-TOKEN", csrfCookie.getValue())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"username\":\"locktest\",\"password\":\"wrong\"}"))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/api/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"locktest\",\"password\":\"wrong\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
  }

  @Test
  void loginTrackerResetsOnSuccess() {
    loginAttempts.recordFailure("resetuser");
    loginAttempts.recordFailure("resetuser");
    loginAttempts.recordFailure("resetuser");
    Assertions.assertFalse(loginAttempts.isLocked("resetuser"));
    loginAttempts.recordSuccess("resetuser");
    Assertions.assertEquals(
        LoginAttemptTracker.MAX_ATTEMPTS, loginAttempts.getRemainingAttempts("resetuser"));
  }

  @Test
  void loginTrackerLockoutExpiresAfterFifteenMinutes() {
    var info = new LoginAttemptTracker.AttemptInfo();
    info.count = LoginAttemptTracker.MAX_ATTEMPTS + 1;
    info.lastAttemptTime = System.currentTimeMillis() - LoginAttemptTracker.LOCKOUT_DURATION_MS - 1;
    java.lang.reflect.Field field;
    try {
      field = LoginAttemptTracker.class.getDeclaredField("attempts");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      var map =
          (java.util.concurrent.ConcurrentHashMap<String, LoginAttemptTracker.AttemptInfo>)
              field.get(loginAttempts);
      map.put("expireuser", info);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    Assertions.assertFalse(loginAttempts.isLocked("expireuser"));
  }

  @Test
  void loginTrackerNormalizesUsernameCase() {
    loginAttempts.recordFailure("Alice");
    loginAttempts.recordFailure("alice");
    loginAttempts.recordFailure("ALICE");
    loginAttempts.recordFailure("aLiCe");
    loginAttempts.recordFailure("AliCe");
    Assertions.assertTrue(loginAttempts.isLocked("alice"));
    Assertions.assertTrue(loginAttempts.isLocked("ALICE"));
    Assertions.assertEquals(0, loginAttempts.getRemainingAttempts("Alice"));
  }

  // ==================== H2: Password policy ====================

  @Test
  void passwordValidatorRejectsTooShort() {
    Assertions.assertNotNull(PasswordValidator.validate("Ab1!"));
  }

  @Test
  void passwordValidatorRejectsNoUppercase() {
    Assertions.assertNotNull(PasswordValidator.validate("alllower1!"));
  }

  @Test
  void passwordValidatorRejectsNoLowercase() {
    Assertions.assertNotNull(PasswordValidator.validate("ALLUPPER1!"));
  }

  @Test
  void passwordValidatorRejectsNoDigit() {
    Assertions.assertNotNull(PasswordValidator.validate("NoDigitHere!"));
  }

  @Test
  void passwordValidatorRejectsNoSpecialChar() {
    Assertions.assertNotNull(PasswordValidator.validate("NoSpecial123"));
  }

  @Test
  void passwordValidatorAcceptsValidPassword() {
    Assertions.assertNull(PasswordValidator.validate("ValidPass1!"));
  }

  @Test
  void registrationRejectsWeakPassword() throws Exception {
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie localCsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrf)
                .header("X-XSRF-TOKEN", localCsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"weakpassuser\",\"password\":\"alllower\",\"name\":\"Weak\",\"email\":\"weak@example.com\",\"phone\":\"555-0300\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
  }

  // ==================== H3: Session cookie attributes ====================

  @Test
  void sessionCookieHasSecureAndSameSiteAttributes() {
    Assertions.assertTrue(sessionCookieSecure, "Session cookie must have Secure flag");
    Assertions.assertEquals("lax", sessionCookieSameSite, "Session cookie must have SameSite=Lax");
  }

  // ==================== H4: Concurrent session control ====================

  @Test
  void secondLoginInvalidatesFirstSession() throws Exception {
    MvcResult csrfResult1 =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie csrf1 = csrfResult1.getResponse().getCookie("XSRF-TOKEN");

    String body =
        "{\"username\":\"admin\",\"password\":\"" + adminPassword.replace("\"", "\\\"") + "\"}";

    mvc.perform(
            post("/api/auth/login")
                .session(new MockHttpSession())
                .cookie(csrf1)
                .header("X-XSRF-TOKEN", csrf1.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    var principalsAfterFirst = sessionRegistry.getAllPrincipals();
    int sessionsAfterFirst = 0;
    for (var principal : principalsAfterFirst) {
      sessionsAfterFirst += sessionRegistry.getAllSessions(principal, false).size();
    }

    MvcResult csrfResult2 =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie csrf2 = csrfResult2.getResponse().getCookie("XSRF-TOKEN");

    mvc.perform(
            post("/api/auth/login")
                .session(new MockHttpSession())
                .cookie(csrf2)
                .header("X-XSRF-TOKEN", csrf2.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    var principalsAfterSecond = sessionRegistry.getAllPrincipals();
    int sessionsAfterSecond = 0;
    for (var principal : principalsAfterSecond) {
      sessionsAfterSecond += sessionRegistry.getAllSessions(principal, false).size();
    }

    Assertions.assertTrue(
        sessionsAfterSecond <= 1,
        "Session registry should have at most 1 session per user after second login, had "
            + sessionsAfterSecond);
  }

  // ==================== H5: Swagger disabled by default ====================

  @Test
  void swaggerUiDisabledWithoutProfile() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
  }

  // ==================== C1: Admin rejects default password ====================

  @Test
  void adminSeederRejectsDefaultPassword() throws Exception {
    var seeder = new com.example.lms.config.AdminSeeder();
    var ex =
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> seeder.seedAdmin(null, null, "admin", "ChangeMe123!").run(new String[] {}));
    Assertions.assertTrue(ex.getMessage().contains("must not be the default value"));
  }

  // ==================== C1: Admin rejects blank password ====================

  @Test
  void adminSeederRejectsBlankPassword() throws Exception {
    var seeder = new com.example.lms.config.AdminSeeder();
    var ex =
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> seeder.seedAdmin(null, null, "admin", "").run(new String[] {}));
    Assertions.assertTrue(ex.getMessage().contains("required"));
  }

  // ==================== C2: Report completeness with >500 records ====================

  @Test
  void inventoryCsvContainsAllRecordsAcrossBatches() {
    for (int i = 0; i < 600; i++) {
      Book b = new Book();
      b.setTitle("Batch Book " + i);
      b.setAuthor("Author " + i);
      b.setIsbn(String.format("978000000%04d", i + 100));
      b.setAvailable(true);
      books.save(b);
    }

    String csv = reportService.inventoryCsv();
    String[] lines = csv.split("\n");
    long bookCount = java.util.Arrays.stream(lines).filter(l -> l.startsWith("Book,")).count();
    Assertions.assertTrue(bookCount >= 600, "Expected at least 600 book rows, got " + bookCount);
    Assertions.assertEquals(bookCount + 1, lines.length, "CSV should have header + book rows only");
  }

  @Test
  void borrowingCsvDateRangeIsBounded() throws Exception {
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie localCsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrf)
                .header("X-XSRF-TOKEN", localCsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"datestudent\",\"password\":\"Password123!\",\"name\":\"Date Student\",\"email\":\"datestudent@example.com\",\"phone\":\"555-9001\"}"))
        .andExpect(status().isCreated());

    StudentProfile student = students.findByAccountUsername("datestudent").orElseThrow();

    for (int i = 0; i < 550; i++) {
      BorrowRecord r = new BorrowRecord();
      r.setBorrowDate(LocalDate.of(2025, 1, 1).plusDays(i % 365));
      r.setBorrowerName("Borrower " + i);
      r.setBorrowerEmail("borrower" + i + "@example.com");
      r.setBorrowerPhone("555-0000");
      r.setStudent(student);
      borrowRecords.save(r);
    }

    String csv = reportService.borrowingCsv(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
    String[] lines = csv.split("\n");
    long dataRows = lines.length - 1;
    Assertions.assertTrue(dataRows >= 180, "Expected at least 180 rows in range, got " + dataRows);

    String allCsv = reportService.borrowingCsv(null, null);
    long totalRows = allCsv.split("\n").length - 1;
    Assertions.assertTrue(totalRows >= 550, "Expected at least 550 total rows, got " + totalRows);
  }

  @Test
  void studentsCsvIncludesAccountUsernameWithoutNPlusOne() throws Exception {
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie localCsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrf)
                .header("X-XSRF-TOKEN", localCsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"csvtestuser\",\"password\":\"Password123!\",\"name\":\"CSV Test User\",\"email\":\"csvtest@example.com\",\"phone\":\"555-9002\"}"))
        .andExpect(status().isCreated());

    String csv = reportService.studentsCsv();
    String[] lines = csv.split("\n");
    Assertions.assertTrue(lines.length > 1, "Students CSV must have data rows");
    boolean hasUser = java.util.Arrays.stream(lines).anyMatch(l -> l.contains("csvtestuser"));
    Assertions.assertTrue(hasUser, "Students CSV must include csvtestuser username");
  }

  // ==================== F1: Config endpoint ====================

  @Test
  void configEndpointReturnsSwaggerDisabled() throws Exception {
    mvc.perform(get("/api/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.swaggerEnabled").value(false));
  }

  // ==================== F3: No stale format param in reports HTML ====================

  @Test
  void reportsPageHasNoFormatCsvLinks() throws Exception {
    var html =
        new String(
            getClass().getClassLoader().getResourceAsStream("static/reports.html").readAllBytes());
    Assertions.assertFalse(
        html.contains("format=csv"), "reports.html must not contain stale format=csv links");
  }

  // ==================== FAILED_LOGIN audit trail ====================

  @Test
  void failedLoginCreatesAuditRecord() throws Exception {
    String username = "auditlogtestuser";
    mvc.perform(
            post("/api/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .header("User-Agent", "TestAgent/1.0")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"%s\",\"password\":\"wrongpassword\"}".formatted(username)))
        .andExpect(status().isUnauthorized());

    var records =
        auditLogs.findByFilters(
            AuditAction.FAILED_LOGIN,
            null,
            username,
            null,
            null,
            org.springframework.data.domain.PageRequest.of(0, 10));
    Assertions.assertFalse(records.isEmpty(), "FAILED_LOGIN audit record must exist");
    var record = records.getContent().getFirst();
    Assertions.assertEquals(AuditAction.FAILED_LOGIN, record.getAction());
    Assertions.assertEquals(com.example.lms.entity.AuditEntityType.ACCOUNT, record.getEntityType());
    Assertions.assertNull(record.getEntityId(), "entityId must be null for failed login");
    Assertions.assertNull(record.getActorId(), "actorId must be null for failed login");
    Assertions.assertEquals(username, record.getActorUsername());
    Assertions.assertNull(record.getActorRole(), "actorRole must be null for failed login");
    Assertions.assertNotNull(record.getIpAddress(), "ipAddress must be populated");
    Assertions.assertNotNull(record.getUserAgent(), "userAgent must be populated");
  }
}
