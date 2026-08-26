package com.example.lms;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrudIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

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

  private String authPost(String url, String content) throws Exception {
    return mvc.perform(
            post(url)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String authGet(String url) throws Exception {
    return mvc.perform(
            get(url)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  // ==================== Magazine CRUD ====================

  @Test
  @Order(1)
  void magazineCrud() throws Exception {
    String created =
        authPost("/api/magazines", "{\"title\":\"National Geographic\",\"publisher\":\"NatGeo\"}");
    long id = json.readTree(created).path("id").asLong();
    Assertions.assertTrue(id > 0);

    mvc.perform(
            get("/api/magazines/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("National Geographic"));

    mvc.perform(
            get("/api/magazines?search=National")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

    mvc.perform(
            delete("/api/magazines/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());

    mvc.perform(
            get("/api/magazines/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNotFound());
  }

  // ==================== Newspaper CRUD ====================

  @Test
  @Order(2)
  void newspaperCrud() throws Exception {
    String created =
        authPost("/api/newspapers", "{\"title\":\"Daily Times\",\"publisher\":\"Times Inc\"}");
    long id = json.readTree(created).path("id").asLong();
    Assertions.assertTrue(id > 0);

    mvc.perform(
            get("/api/newspapers/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Daily Times"));

    mvc.perform(
            delete("/api/newspapers/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());
  }

  // ==================== Student update + delete ====================

  @Test
  @Order(3)
  void studentUpdateAndDelete() throws Exception {
    String created =
        authPost(
            "/api/students",
            "{\"username\":\"teststudent\",\"password\":\"Password123!\",\"name\":\"Test Student\",\"email\":\"test@example.com\",\"phone\":\"555-0001\"}");
    long id = json.readTree(created).path("id").asLong();

    mvc.perform(
            put("/api/students/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"teststudent\",\"name\":\"Updated Name\",\"email\":\"updated@example.com\",\"phone\":\"555-0002\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"));

    mvc.perform(
            delete("/api/students/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());

    mvc.perform(
            get("/api/students/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNotFound());
  }

  // ==================== Librarian update + delete ====================

  @Test
  @Order(4)
  void librarianUpdateAndDelete() throws Exception {
    String created =
        authPost(
            "/api/librarians",
            "{\"username\":\"testlibrarian\",\"password\":\"Password123!\",\"name\":\"Test Librarian\",\"age\":25,\"phone\":\"555-0003\"}");
    long id = json.readTree(created).path("id").asLong();

    mvc.perform(
            put("/api/librarians/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"testlibrarian\",\"name\":\"Updated Librarian\",\"age\":30,\"phone\":\"555-0004\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Librarian"));

    mvc.perform(
            delete("/api/librarians/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());

    mvc.perform(
            get("/api/librarians/{id}", id)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNotFound());
  }

  // ==================== Dashboard ====================

  @Test
  @Order(5)
  void dashboardReturnsCounts() throws Exception {
    String body = authGet("/api/dashboard");
    Assertions.assertTrue(body.contains("totalBooks"));
    Assertions.assertTrue(body.contains("totalStudents"));
  }

  // ==================== Audit log ====================

  @Test
  @Order(6)
  void auditLogAccessibleByAdmin() throws Exception {
    mvc.perform(
            get("/api/audit?page=0&size=5")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk());
  }

  // ==================== Duplicate username conflicts ====================

  @Test
  @Order(7)
  void duplicateUsernameReturnsConflict() throws Exception {
    authPost(
        "/api/students",
        "{\"username\":\"dupstudent\",\"password\":\"Password123!\",\"name\":\"First\",\"email\":\"a@example.com\",\"phone\":\"555-0010\"}");
    mvc.perform(
            post("/api/students")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"dupstudent\",\"password\":\"Password123!\",\"name\":\"Second\",\"email\":\"b@example.com\",\"phone\":\"555-0011\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  @Order(8)
  void duplicateEmailReturnsConflict() throws Exception {
    authPost(
        "/api/students",
        "{\"username\":\"dupemail\",\"password\":\"Password123!\",\"name\":\"First\",\"email\":\"dup@example.com\",\"phone\":\"555-0012\"}");
    mvc.perform(
            post("/api/students")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"dupemail2\",\"password\":\"Password123!\",\"name\":\"Second\",\"email\":\"dup@example.com\",\"phone\":\"555-0013\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("Email is already registered."));
  }
}
