package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.repository.*;
import com.example.lms.service.AnalyticsService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock BookRepository books;
  @Mock StudentProfileRepository students;
  @Mock LibrarianProfileRepository librarians;
  @Mock BorrowRecordRepository borrowRecords;

  @InjectMocks AnalyticsService service;

  private StudentProfile student;

  @BeforeEach
  void setUp() {
    Account account = new Account();
    setId(account, 10L);
    account.setUsername("s1");

    student = new StudentProfile();
    setId(student, 1L);
    student.setAccount(account);
    student.setName("Alice");
    student.setEmail("alice@example.com");
  }

  @Test
  void dashboardReturnsCorrectCounts() {
    when(books.count()).thenReturn(100L);
    when(students.count()).thenReturn(50L);
    when(librarians.count()).thenReturn(5L);
    when(books.countByAvailable(false)).thenReturn(30L);
    when(books.countByAvailable(true)).thenReturn(70L);
    when(borrowRecords.countActiveOverdue(any(), any())).thenReturn(0L);

    AnalyticsDashboardResponse resp = service.dashboard();

    assertEquals(100L, resp.totalBooks());
    assertEquals(50L, resp.totalStudents());
    assertEquals(5L, resp.totalLibrarians());
    assertEquals(30L, resp.borrowedBooks());
    assertEquals(70L, resp.availableBooks());
    assertEquals(0L, resp.overdueCount());
  }

  @Test
  void dashboardAllZerosWhenEmpty() {
    when(books.count()).thenReturn(0L);
    when(students.count()).thenReturn(0L);
    when(librarians.count()).thenReturn(0L);
    when(books.countByAvailable(false)).thenReturn(0L);
    when(books.countByAvailable(true)).thenReturn(0L);
    when(borrowRecords.countActiveOverdue(any(), any())).thenReturn(0L);

    AnalyticsDashboardResponse resp = service.dashboard();

    assertEquals(0L, resp.totalBooks());
    assertEquals(0L, resp.totalStudents());
    assertEquals(0L, resp.overdueCount());
  }

  @Test
  void dashboardCountsOnlyOverdueRecords() {
    when(books.count()).thenReturn(10L);
    when(students.count()).thenReturn(1L);
    when(librarians.count()).thenReturn(0L);
    when(books.countByAvailable(false)).thenReturn(3L);
    when(books.countByAvailable(true)).thenReturn(7L);
    when(borrowRecords.countActiveOverdue(any(), any())).thenReturn(2L);

    AnalyticsDashboardResponse resp = service.dashboard();

    assertEquals(2L, resp.overdueCount(), "Should count only overdue unreturned records");
  }

  @Test
  void trendsMapsRawProjectionToDto() {
    Object[] row1 = new Object[] {(Number) 2026, (Number) 7, (Number) 15L};
    Object[] row2 = new Object[] {(Number) 2026, (Number) 8, (Number) 22L};
    when(borrowRecords.findMonthlyTrends()).thenReturn(List.<Object[]>of(row1, row2));

    List<MonthlyTrend> result = service.trends();

    assertEquals(2, result.size());
    assertEquals(new MonthlyTrend(2026, 7, 15L), result.get(0));
    assertEquals(new MonthlyTrend(2026, 8, 22L), result.get(1));
  }

  @Test
  void trendsEmptyWhenNoData() {
    when(borrowRecords.findMonthlyTrends()).thenReturn(List.of());

    List<MonthlyTrend> result = service.trends();

    assertTrue(result.isEmpty());
  }

  @Test
  void topBooksMapsProjections() {
    Object[] row1 = new Object[] {1L, "Clean Code", "Robert Martin", (Number) 42L};
    Object[] row2 = new Object[] {2L, "DDD", "Eric Evans", (Number) 30L};
    when(borrowRecords.findTopBooks(eq(PageRequest.of(0, 10))))
        .thenReturn(List.<Object[]>of(row1, row2));

    List<TopBookResponse> result = service.topBooks(10);

    assertEquals(2, result.size());
    assertEquals(new TopBookResponse(1L, "Clean Code", "Robert Martin", 42L), result.get(0));
    assertEquals(new TopBookResponse(2L, "DDD", "Eric Evans", 30L), result.get(1));
  }

  @Test
  void topBooksEmptyWhenNoData() {
    when(borrowRecords.findTopBooks(eq(PageRequest.of(0, 10)))).thenReturn(List.of());

    List<TopBookResponse> result = service.topBooks(10);

    assertTrue(result.isEmpty());
  }

  @Test
  void topReadersMapsProjections() {
    Object[] row1 = new Object[] {1L, "Alice", "alice@ex.com", (Number) 15L};
    when(borrowRecords.findTopReaders(eq(PageRequest.of(0, 5))))
        .thenReturn(List.<Object[]>of(row1));

    List<TopReaderResponse> result = service.topReaders(5);

    assertEquals(1, result.size());
    assertEquals(new TopReaderResponse(1L, "Alice", "alice@ex.com", 15L), result.get(0));
  }

  @Test
  void topReadersEmptyWhenNoData() {
    when(borrowRecords.findTopReaders(eq(PageRequest.of(0, 5)))).thenReturn(List.of());

    List<TopReaderResponse> result = service.topReaders(5);

    assertTrue(result.isEmpty());
  }

  @Test
  void overdueSummaryFiltersCorrectly() {
    BorrowRecord overdue =
        makeBorrowRecord(1L, LocalDate.of(2026, 8, 1), LocalDate.now().minusDays(5), null);
    overdue.getBook().setTitle("Overdue Book");

    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of(overdue));

    OverdueSummaryResponse resp = service.overdue();

    assertEquals(1L, resp.totalOverdue());
    assertEquals(1, resp.items().size());
    assertEquals(1L, resp.items().get(0).id());
    assertEquals("Overdue Book", resp.items().get(0).itemTitle());
    assertEquals("Alice", resp.items().get(0).borrowerName());
    assertEquals(LocalDate.of(2026, 8, 1), resp.items().get(0).borrowDate());
    assertTrue(resp.items().get(0).daysOverdue() > 0);
  }

  @Test
  void overdueSummaryEmptyWhenNoneOverdue() {
    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of());

    OverdueSummaryResponse resp = service.overdue();

    assertEquals(0L, resp.totalOverdue());
    assertTrue(resp.items().isEmpty());
  }

  @Test
  void overdueSummaryEmptyWhenNoBorrowRecords() {
    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of());

    OverdueSummaryResponse resp = service.overdue();

    assertEquals(0L, resp.totalOverdue());
    assertTrue(resp.items().isEmpty());
  }

  @Test
  void overdueUsesEffectiveDueDateWithExplicitDueDate() {
    LocalDate dueDate = LocalDate.now().minusDays(3);
    LocalDate borrowDate = dueDate.minusDays(10);
    BorrowRecord r = makeBorrowRecord(1L, borrowDate, dueDate, null);
    r.setDueDate(dueDate);

    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of(r));

    OverdueSummaryResponse resp = service.overdue();

    assertEquals(1L, resp.totalOverdue());
    assertEquals(3L, resp.items().get(0).daysOverdue());
  }

  @Test
  void overdueFallsBackToBorrowDatePlus14() {
    LocalDate borrowDate = LocalDate.now().minusDays(20);
    BorrowRecord r = makeBorrowRecord(1L, borrowDate, null, null);

    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of(r));

    OverdueSummaryResponse resp = service.overdue();

    assertEquals(1L, resp.totalOverdue());
    assertEquals(6L, resp.items().get(0).daysOverdue());
  }

  @Test
  void overdueItemTitleFromMagazine() {
    BorrowRecord r = new BorrowRecord();
    setId(r, 10L);
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setDueDate(LocalDate.now().minusDays(3));
    Magazine mag = new Magazine();
    setId(mag, 20L);
    mag.setTitle("Time Magazine");
    r.setMagazine(mag);
    r.setStudent(student);
    r.setBorrowerName(student.getName());

    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of(r));

    OverdueSummaryResponse resp = service.overdue();

    assertEquals("Time Magazine", resp.items().get(0).itemTitle());
  }

  @Test
  void overdueItemTitleFromNewspaper() {
    BorrowRecord r = new BorrowRecord();
    setId(r, 11L);
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setDueDate(LocalDate.now().minusDays(2));
    Newspaper np = new Newspaper();
    setId(np, 30L);
    np.setTitle("Daily Times");
    r.setNewspaper(np);
    r.setStudent(student);
    r.setBorrowerName(student.getName());

    when(borrowRecords.findActiveOverdue(any(), any())).thenReturn(List.of(r));

    OverdueSummaryResponse resp = service.overdue();

    assertEquals("Daily Times", resp.items().get(0).itemTitle());
  }

  // ==================== Helpers ====================

  private static void setId(Object entity, Long id) {
    try {
      Field field = entity.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set id via reflection", e);
    }
  }

  private BorrowRecord makeBorrowRecord(
      Long id, LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate) {
    BorrowRecord r = new BorrowRecord();
    setId(r, id);
    r.setBorrowDate(borrowDate);
    r.setDueDate(dueDate);
    r.setReturnDate(returnDate);
    r.setStudent(student);
    r.setBorrowerName(student.getName());
    r.setBorrowerEmail(student.getEmail());
    r.setBorrowerPhone(student.getPhone());
    Book book = new Book();
    setId(book, id);
    book.setTitle("Book " + id);
    r.setBook(book);
    return r;
  }
}
