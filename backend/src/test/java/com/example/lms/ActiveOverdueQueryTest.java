package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lms.entity.Book;
import com.example.lms.entity.BorrowRecord;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.BorrowRecordRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:overdue-query-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class ActiveOverdueQueryTest {

  @Autowired BorrowRecordRepository borrowRecords;
  @Autowired BookRepository books;

  @Test
  void countAndFindActiveOverdueMatchExpectedSemantics() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Overdue Test Book"));

    // Case 1: active, explicit dueDate in the past → overdue
    save(book, today.minusDays(10), today.minusDays(3), null);

    // Case 2: active, explicit dueDate in the future → not overdue
    save(book, today.minusDays(20), today.plusDays(5), null);

    // Case 3: active, explicit dueDate exactly today → not overdue (strict isAfter)
    save(book, today.minusDays(20), today, null);

    // Case 4: active, null dueDate, borrowDate > 14 days ago → overdue (fallback)
    save(book, today.minusDays(20), null, null);

    // Case 5: active, null dueDate, borrowDate exactly 14 days ago → not overdue
    save(book, today.minusDays(14), null, null);

    // Case 6: active, null dueDate, borrowDate < 14 days ago → not overdue
    save(book, today.minusDays(7), null, null);

    // Case 7: returned, effective overdue → excluded (returnDate non-null)
    save(book, today.minusDays(20), today.minusDays(10), today.minusDays(1));

    long count = borrowRecords.countActiveOverdue(today, cutoff);
    assertThat(count).isEqualTo(2);

    var items = borrowRecords.findActiveOverdue(today, cutoff);
    assertThat(items).hasSize(2);
    assertThat(items).allMatch(r -> r.getReturnDate() == null);
  }

  @Test
  void activeNoExplicitOverdueReturnsEmpty() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("No Overdue Book"));

    // active, explicit future dueDate
    save(book, today.minusDays(5), today.plusDays(10), null);

    // active, null dueDate, borrowDate within 14 days
    save(book, today.minusDays(3), null, null);

    assertThat(borrowRecords.countActiveOverdue(today, cutoff)).isEqualTo(0);
    assertThat(borrowRecords.findActiveOverdue(today, cutoff)).isEmpty();
  }

  @Test
  void returnedOverdueRecordsExcluded() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Returned Overdue Book"));

    // returned with past dueDate — should NOT appear
    save(book, today.minusDays(30), today.minusDays(10), today.minusDays(1));

    assertThat(borrowRecords.countActiveOverdue(today, cutoff)).isEqualTo(0);
    assertThat(borrowRecords.findActiveOverdue(today, cutoff)).isEmpty();
  }

  @Test
  void fallbackBoundaryExactly14DaysNotOverdue() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Boundary Book"));

    // null dueDate, borrowDate exactly 14 days ago → not overdue
    save(book, today.minusDays(14), null, null);

    assertThat(borrowRecords.countActiveOverdue(today, cutoff)).isEqualTo(0);
  }

  @Test
  void explicitDueDateBoundaryExactlyTodayNotOverdue() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Today Boundary Book"));

    // dueDate exactly today → not overdue
    save(book, today.minusDays(20), today, null);

    assertThat(borrowRecords.countActiveOverdue(today, cutoff)).isEqualTo(0);
  }

  private BorrowRecord save(
      Book book, LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate) {
    BorrowRecord r = new BorrowRecord();
    r.setBook(book);
    r.setBorrowDate(borrowDate);
    r.setDueDate(dueDate);
    r.setReturnDate(returnDate);
    r.setBorrowerName("Test Borrower");
    r.setBorrowerEmail("test@example.com");
    r.setBorrowerPhone("555-0000");
    return borrowRecords.saveAndFlush(r);
  }

  private Book makeBook(String title) {
    Book b = new Book();
    b.setTitle(title);
    b.setAuthor("Test Author");
    return b;
  }
}
