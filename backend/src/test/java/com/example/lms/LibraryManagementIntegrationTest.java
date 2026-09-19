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

  @Test
  void selfRegistrationFlow() throws Exception {
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie localCsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    String registerBody =
        "{\"username\":\"newstudent\",\"password\":\"Password123!\",\"name\":\"New Student\",\"email\":\"newstudent@example.com\",\"phone\":\"555-0199\"}";

    // Successful registration
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isCreated());

    // Login with new credentials
    String loginBody = "{\"username\":\"newstudent\",\"password\":\"Password123!\"}";
    mvc.perform(
            post("/api/auth/login")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STUDENT"));

    // Duplicate username → 409 CONFLICT with ApiErrorResponse body
    String dupUserBody =
        "{\"username\":\"newstudent\",\"password\":\"Password123!\",\"name\":\"Another\",\"email\":\"another@example.com\",\"phone\":\"555-0200\"}";
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(dupUserBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("Username is already in use."))
        .andExpect(jsonPath("$.path").value("/api/auth/register"));

    // Duplicate email → 409 CONFLICT with ApiErrorResponse body
    String dupEmailBody =
        "{\"username\":\"anotherstudent\",\"password\":\"Password123!\",\"name\":\"Another\",\"email\":\"newstudent@example.com\",\"phone\":\"555-0200\"}";
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(dupEmailBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("Email is already registered."))
        .andExpect(jsonPath("$.path").value("/api/auth/register"));
  }

  @Test
  void registrationValidationErrorsReturnFieldErrors() throws Exception {
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie localCsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    // Username shorter than the 3-character minimum
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"ab\",\"password\":\"Password123!\",\"name\":\"Valid Name\",\"email\":\"valid@example.com\",\"phone\":\"555-0201\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]", not(empty())));

    // Password shorter than the 8-character minimum
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"validuser1\",\"password\":\"Short12\",\"name\":\"Valid Name\",\"email\":\"valid1@example.com\",\"phone\":\"555-0202\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]", not(empty())));

    // Malformed email
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"validuser2\",\"password\":\"Password123!\",\"name\":\"Valid Name\",\"email\":\"not-an-email\",\"phone\":\"555-0203\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath(
                "$.fieldErrors[?(@.field == 'email')].message", hasItem("Invalid email address.")));

    // Blank required fields
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"\",\"password\":\"\",\"name\":\"\",\"email\":\"\",\"phone\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors", not(empty())));
  }

  @Test
  void registerWithoutCsrfIsForbidden() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"nocsrfuser\",\"password\":\"Password123!\",\"name\":\"No CSRF\",\"email\":\"nocsrf@example.com\",\"phone\":\"555-0204\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void profileEndpointReturnsAuthenticatedUserProfile() throws Exception {
    // Re-uses adminSession established in @BeforeEach (password read from ${lms.admin.password})
    mvc.perform(get("/api/profile").session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void swaggerDocsAndUiAreDisabledByDefault() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
  }

  @Test
  void bookCrudSupportsCategory() throws Exception {
    MvcResult book =
        mvc.perform(
                post("/api/books")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Structure and Interpretation of Computer Programs\",\"author\":\"Harold Abelson\",\"category\":\"Computer Science\",\"isbn\":\"9780262510875\",\"publishedDate\":\"1996-07-25\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.category").value("Computer Science"))
            .andReturn();

    long bookId = json.readTree(book.getResponse().getContentAsString()).path("id").asLong();

    mvc.perform(
            get("/api/books/" + bookId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("Computer Science"));
  }

  @Test
  void actuatorHealthEndpointIsPubliclyAccessible() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void defaultBorrowRecordListReturnsAssociationsCorrectly() throws Exception {
    MvcResult student =
        mvc.perform(
                post("/api/students")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"liststudent\",\"password\":\"Password123!\",\"name\":\"List Test\",\"email\":\"list@example.com\",\"phone\":\"555-0210\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long studentId = json.readTree(student.getResponse().getContentAsString()).path("id").asLong();

    MvcResult book =
        mvc.perform(
                post("/api/books")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"List Test Book\",\"author\":\"Author\",\"isbn\":\"9780000000091\",\"publishedDate\":\"2026-01-01\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long bookId = json.readTree(book.getResponse().getContentAsString()).path("id").asLong();

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
                        + ",\"borrowerName\":\"List Test\",\"borrowerEmail\":\"list@example.com\",\"borrowerPhone\":\"555-0210\",\"borrowDate\":\"2026-09-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("BORROWED"));

    mvc.perform(
            get("/api/borrow-records")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$.content[?(@.itemTitle == 'List Test Book')]").exists())
        .andExpect(jsonPath("$.content[0].itemType").value("BOOK"))
        .andExpect(jsonPath("$.content[0].itemId").isNumber())
        .andExpect(jsonPath("$.content[0].studentId").isNumber())
        .andExpect(jsonPath("$.content[0].status").value("BORROWED"))
        .andExpect(jsonPath("$.totalElements").isNumber())
        .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void borrowedStatusFilterReturnsOnlyActiveRecords() throws Exception {
    MvcResult student =
        mvc.perform(
                post("/api/students")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"filterstudent\",\"password\":\"Password123!\",\"name\":\"Filter\",\"email\":\"filter@example.com\",\"phone\":\"555-0211\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long studentId = json.readTree(student.getResponse().getContentAsString()).path("id").asLong();

    MvcResult book =
        mvc.perform(
                post("/api/books")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Filter Test Book\",\"author\":\"Author\",\"isbn\":\"9780000000092\",\"publishedDate\":\"2026-01-01\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long bookId = json.readTree(book.getResponse().getContentAsString()).path("id").asLong();

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
                            + ",\"borrowerName\":\"Filter\",\"borrowerEmail\":\"filter@example.com\",\"borrowerPhone\":\"555-0211\",\"borrowDate\":\"2026-09-01\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long recordId = json.readTree(borrow.getResponse().getContentAsString()).path("id").asLong();

    mvc.perform(
            post("/api/borrow-records/{id}/return", recordId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());

    mvc.perform(
            get("/api/borrow-records?status=BORROWED")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.itemTitle == 'Filter Test Book')]").doesNotExist());

    mvc.perform(
            get("/api/borrow-records?status=RETURNED")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.itemTitle == 'Filter Test Book')]").exists())
        .andExpect(jsonPath("$.content[0].status").value("RETURNED"));
  }

  @Test
  void searchBorrowRecordsReturnsMatchingResults() throws Exception {
    MvcResult student =
        mvc.perform(
                post("/api/students")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"searchstudent\",\"password\":\"Password123!\",\"name\":\"Search\",\"email\":\"search@example.com\",\"phone\":\"555-0212\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long studentId = json.readTree(student.getResponse().getContentAsString()).path("id").asLong();

    MvcResult book =
        mvc.perform(
                post("/api/books")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Searchable\",\"author\":\"Author\",\"isbn\":\"9780000000093\",\"publishedDate\":\"2026-01-01\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long bookId = json.readTree(book.getResponse().getContentAsString()).path("id").asLong();

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
                        + ",\"borrowerName\":\"Unique Searcher\",\"borrowerEmail\":\"unique-search@example.com\",\"borrowerPhone\":\"555\",\"borrowDate\":\"2026-09-01\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/borrow-records?query=unique-search")
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void unsupportedHttpMethodOnAuthEndpointsReturns405() throws Exception {
    // GET /api/auth/login should return 405 Method Not Allowed
    mvc.perform(get("/api/auth/login"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.message").value("Request method 'GET' is not supported"))
        .andExpect(jsonPath("$.path").value("/api/auth/login"));

    // GET /api/auth/register should return 405 Method Not Allowed
    mvc.perform(get("/api/auth/register"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.message").value("Request method 'GET' is not supported"))
        .andExpect(jsonPath("$.path").value("/api/auth/register"));
  }

  @Test
  void validPostOnAuthEndpointsRemainsUnchanged() throws Exception {
    // Verify POST /api/auth/login still works correctly
    String loginBody =
        "{\"username\":\"admin\",\"password\":\"" + adminPassword.replace("\"", "\\\"") + "\"}";
    mvc.perform(
            post("/api/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));

    // Verify POST /api/auth/register still works correctly
    MvcResult csrfResult =
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie localCsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    String registerBody =
        "{\"username\":\"posttest"
            + System.currentTimeMillis()
            + "\",\"password\":\"Password123!\",\"name\":\"Post Test\",\"email\":\"posttest"
            + System.currentTimeMillis()
            + "@example.com\",\"phone\":\"555-0300\"}";
    mvc.perform(
            post("/api/auth/register")
                .cookie(localCsrfCookie)
                .header("X-XSRF-TOKEN", localCsrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isCreated());
  }
}
