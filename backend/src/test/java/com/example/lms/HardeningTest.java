package com.example.lms;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lms.entity.BorrowRecord;
import com.example.lms.repository.BorrowRecordRepository;
import com.example.lms.util.OverdueCalculator;
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
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class HardeningTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired BorrowRecordRepository borrowRecords;

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

  // ==================== Student deletion with borrow history ====================

  @Test
  void deleteStudentWithBorrowHistoryReturnsConflict() throws Exception {
    String created =
        authPost(
            "/api/students",
            "{\"username\":\"hist-student\",\"password\":\"Password123!\",\"name\":\"History Student\",\"email\":\"hist@example.com\",\"phone\":\"555-0100\"}");
    long studentId = json.readTree(created).path("id").asLong();

    MvcResult book =
        mvc.perform(
                post("/api/books")
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Test Book\",\"author\":\"Author\",\"isbn\":\"9780000000099\",\"publishedDate\":\"2024-01-01\"}"))
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
                        + ",\"borrowerName\":\"History Student\",\"borrowerEmail\":\"hist@example.com\",\"borrowerPhone\":\"555-0100\",\"borrowDate\":\"2026-07-10\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            delete("/api/students/{id}", studentId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("A student with borrow history cannot be deleted."));
  }

  @Test
  void deleteStudentWithoutBorrowHistorySucceeds() throws Exception {
    String created =
        authPost(
            "/api/students",
            "{\"username\":\"clean-student\",\"password\":\"Password123!\",\"name\":\"Clean Student\",\"email\":\"clean@example.com\",\"phone\":\"555-0101\"}");
    long studentId = json.readTree(created).path("id").asLong();

    mvc.perform(
            delete("/api/students/{id}", studentId)
                .session(adminSession)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());
  }

  // ==================== OverdueCalculator unit tests ====================

  @Test
  void effectiveDueDateReturnsExplicitDueDate() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.of(2026, 8, 1));
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    Assertions.assertEquals(LocalDate.of(2026, 8, 1), OverdueCalculator.effectiveDueDate(r));
  }

  @Test
  void effectiveDueDateFallsBackToBorrowDatePlus14() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(null);
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    Assertions.assertEquals(LocalDate.of(2026, 7, 15), OverdueCalculator.effectiveDueDate(r));
  }

  @Test
  void effectiveDueDateReturnsNullWhenBothDatesNull() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(null);
    r.setBorrowDate(null);
    Assertions.assertNull(OverdueCalculator.effectiveDueDate(r));
  }

  @Test
  void isOverdueReturnsTrueWhenReturnedAfterDueDate() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.of(2026, 7, 10));
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setReturnDate(LocalDate.of(2026, 7, 15));
    Assertions.assertTrue(OverdueCalculator.isOverdue(r));
  }

  @Test
  void isOverdueReturnsFalseWhenReturnedOnDueDate() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.of(2026, 7, 10));
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setReturnDate(LocalDate.of(2026, 7, 10));
    Assertions.assertFalse(OverdueCalculator.isOverdue(r));
  }

  @Test
  void isOverdueReturnsFalseWhenReturnedBeforeDueDate() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.of(2026, 7, 10));
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setReturnDate(LocalDate.of(2026, 7, 5));
    Assertions.assertFalse(OverdueCalculator.isOverdue(r));
  }

  @Test
  void isOverdueUsesNowWhenNotReturned() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.now().minusDays(3));
    r.setBorrowDate(LocalDate.now().minusDays(17));
    r.setReturnDate(null);
    Assertions.assertTrue(OverdueCalculator.isOverdue(r));
  }

  @Test
  void isOverdueReturnsFalseForNullRecord() {
    Assertions.assertFalse(OverdueCalculator.isOverdue(null));
  }

  @Test
  void daysOverdueReturnsCorrectCount() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.of(2026, 7, 10));
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setReturnDate(LocalDate.of(2026, 7, 15));
    Assertions.assertEquals(5, OverdueCalculator.daysOverdue(r));
  }

  @Test
  void daysOverdueReturnsZeroWhenNotOverdue() {
    BorrowRecord r = new BorrowRecord();
    r.setDueDate(LocalDate.of(2026, 7, 10));
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setReturnDate(LocalDate.of(2026, 7, 10));
    Assertions.assertEquals(0, OverdueCalculator.daysOverdue(r));
  }

  @Test
  void daysOverdueReturnsZeroForNullRecord() {
    Assertions.assertEquals(0, OverdueCalculator.daysOverdue(null));
  }

  private String authPost(String url, String body) throws Exception {
    MvcResult result =
        mvc.perform(
                post(url)
                    .session(adminSession)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return result.getResponse().getContentAsString();
  }
}
