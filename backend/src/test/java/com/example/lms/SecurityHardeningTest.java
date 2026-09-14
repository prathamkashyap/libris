package com.example.lms;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lms.repository.BorrowRecordRepository;
import com.example.lms.security.LoginAttemptTracker;
import com.example.lms.security.PasswordValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired BorrowRecordRepository borrowRecords;
  @Autowired LoginAttemptTracker loginAttempts;

  @Value("${lms.admin.password}")
  String adminPassword;

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

  // ==================== H5: Swagger disabled by default ====================

  @Test
  void swaggerUiDisabledWithoutProfile() throws Exception {
    // Swagger paths are not permitAll when springdoc.swagger-ui.enabled=false,
    // so unauthenticated requests should get 401 (secured by auth)
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
}
