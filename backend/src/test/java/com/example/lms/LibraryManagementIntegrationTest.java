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
class LibraryManagementIntegrationTest {
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
    Assertions.assertNotNull(csrfCookie, "CSRF endpoint must issue XSRF-TOKEN cookie");

    String loginBody =
        "{\"username\":\"admin\",\"password\":\"" + adminPassword.replace("\"", "\\\"") + "\"}";
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andReturn();
    adminSession = (MockHttpSession) result.getRequest().getSession(false);
  }

  @Test
  void unauthenticatedAndForbiddenResponsesUseApiErrorShape() throws Exception {
    mvc.perform(get("/api/books"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(
            get("/api/librarians")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.user("lib")
                        .roles("LIBRARIAN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void fullBooksPeopleBorrowReturnAndLogoutFlow() throws Exception {
    MvcResult student =
        mvc.perform(
                post("/api/students")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"alice\",\"password\":\"Password123!\",\"name\":\"Alice\",\"email\":\"alice@example.com\",\"phone\":\"555-0101\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andReturn();
    long studentId = json.readTree(student.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(
            post("/api/librarians")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"libby\",\"password\":\"Password123!\",\"name\":\"Libby\",\"age\":30,\"phone\":\"555-0102\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("LIBRARIAN"));
    MvcResult book =
        mvc.perform(
                post("/api/books")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Clean Code\",\"author\":\"Robert Martin\",\"isbn\":\"9780132350884\",\"publishedDate\":\"2008-08-01\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.available").value(true))
            .andReturn();
    long bookId = json.readTree(book.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(
            get("/api/books?search=Clean")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
    MvcResult borrow =
        mvc.perform(
                post("/api/borrow-records")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"bookId\":"
                            + bookId
                            + ",\"studentId\":"
                            + studentId
                            + ",\"borrowerName\":\"Ignored\",\"borrowerEmail\":\"ignored@example.com\",\"borrowerPhone\":\"0\",\"borrowDate\":\"2026-07-23\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("BORROWED"))
            .andExpect(jsonPath("$.borrowerName").value("Alice"))
            .andReturn();
    long recordId = json.readTree(borrow.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(
            post("/api/borrow-records")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"bookId\":"
                        + bookId
                        + ",\"studentId\":"
                        + studentId
                        + ",\"borrowerName\":\"Alice\",\"borrowerEmail\":\"alice@example.com\",\"borrowerPhone\":\"555\",\"borrowDate\":\"2026-07-23\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
    mvc.perform(
            post("/api/borrow-records/{id}/return", recordId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/borrow-records/{id}/return", recordId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ALREADY_RETURNED"));
    mvc.perform(
            delete("/api/books/{id}", bookId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/auth/logout")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());
  }

  @Test
  void invalidBookRequestReturnsFieldErrors() throws Exception {
    mvc.perform(
            post("/api/books")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors", not(empty())));
  }

  @Test
  void duplicateIsbnReturnsConflictAndValidationReturnsFieldMessage() throws Exception {
    String isbn = "9780000000002";
    String book =
        "{\"title\":\"Unique ISBN Test\",\"author\":\"Test Author\",\"isbn\":\"" + isbn + "\"}";
    mvc.perform(
            post("/api/books")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(book))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/books")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(book))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("ISBN already exists."));
    mvc.perform(
            post("/api/students")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"invalid-email\",\"password\":\"Password123!\",\"name\":\"Invalid Email\",\"email\":\"not-an-email\",\"phone\":\"555-0109\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath(
                "$.fieldErrors[?(@.field == 'email')].message", hasItem("Invalid email address.")));
  }
}
