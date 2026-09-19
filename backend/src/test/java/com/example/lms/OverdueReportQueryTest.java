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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:overdue-report-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class OverdueReportQueryTest {

  @Autowired BorrowRecordRepository borrowRecords;
  @Autowired BookRepository books;

  @Test
  void paginatedQueryReturnsOnlyOverdueRecords() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Report Test Book"));

    // Case 1: active + explicit dueDate in past → included
    var r1 = save(book, today.minusDays(20), today.minusDays(5), null);

    // Case 2: active + explicit dueDate in future → excluded
    save(book, today.minusDays(20), today.plusDays(5), null);

    // Case 3: active + explicit dueDate exactly today → excluded
    save(book, today.minusDays(20), today, null);

    // Case 4: active + null dueDate + borrowDate older than 14 days → included
    var r4 = save(book, today.minusDays(20), null, null);

    // Case 5: active + null dueDate + borrowDate exactly 14 days ago → excluded
    save(book, today.minusDays(14), null, null);

    // Case 6: active + null dueDate + borrowDate less than 14 days old → excluded
    save(book, today.minusDays(7), null, null);

    // Case 7: returned + overdue effective date → excluded
    save(book, today.minusDays(20), today.minusDays(10), today.minusDays(1));

    var results =
        borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(results).hasSize(2);
    assertThat(results)
        .extracting(BorrowRecord::getId)
        .containsExactlyInAnyOrder(r1.getId(), r4.getId());
  }

  @Test
  void paginatedQueryKeysetPaginationWorks() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Pagination Book"));

    var r1 = save(book, today.minusDays(20), today.minusDays(10), null);
    var r2 = save(book, today.minusDays(25), today.minusDays(8), null);
    var r3 = save(book, today.minusDays(30), today.minusDays(3), null);

    // First page
    var page1 = borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 2));
    assertThat(page1).hasSize(2);

    // Second page using lastId from first page
    long lastId = page1.get(page1.size() - 1).getId();
    var page2 =
        borrowRecords.findActiveOverduePaginated(today, cutoff, lastId, PageRequest.of(0, 2));
    assertThat(page2).hasSize(1);
    assertThat(page2.get(0).getId()).isEqualTo(r3.getId());

    // All records together
    var all = borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(all).hasSize(3);
  }

  @Test
  void paginatedQueryExcludesReturnedRecords() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Returned Book"));

    // Returned record with overdue effective date
    save(book, today.minusDays(30), today.minusDays(10), today.minusDays(1));

    var results =
        borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(results).isEmpty();
  }

  @Test
  void paginatedQueryAgreesWithOverdueCalculatorSemantics() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Semantics Book"));

    // Various combinations
    var r1 = save(book, today.minusDays(20), today.minusDays(5), null); // overdue
    var r2 = save(book, today.minusDays(20), today.plusDays(5), null); // not overdue
    save(book, today.minusDays(20), today, null); // not overdue (boundary)
    var r4 = save(book, today.minusDays(20), null, null); // overdue (fallback)
    save(book, today.minusDays(14), null, null); // not overdue (boundary)
    save(book, today.minusDays(7), null, null); // not overdue
    save(book, today.minusDays(20), today.minusDays(10), today.minusDays(1)); // returned

    var results =
        borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(results).hasSize(2);

    // Verify each returned record matches OverdueCalculator semantics
    for (BorrowRecord r : results) {
      assertThat(r.getReturnDate()).isNull();
      assertThat(com.example.lms.util.OverdueCalculator.isOverdue(r)).isTrue();
    }

    // Verify the two overdue records are the ones we expect
    assertThat(results)
        .extracting(BorrowRecord::getId)
        .containsExactlyInAnyOrder(r1.getId(), r4.getId());
  }

  @Test
  void explicitDueDateBoundaryExactlyTodayNotOverdue() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Today Boundary"));

    // dueDate exactly today → not overdue
    save(book, today.minusDays(20), today, null);

    var results =
        borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(results).isEmpty();
  }

  @Test
  void fallbackBoundaryExactly14DaysNotOverdue() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("Fallback Boundary"));

    // null dueDate, borrowDate exactly 14 days ago → not overdue
    save(book, today.minusDays(14), null, null);

    var results =
        borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(results).isEmpty();
  }

  @Test
  void emptyResultWhenNoOverdueExist() {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.minusDays(14);

    Book book = books.saveAndFlush(makeBook("No Overdue Book"));

    // All not overdue
    save(book, today.minusDays(5), today.plusDays(10), null);
    save(book, today.minusDays(3), null, null);

    var results =
        borrowRecords.findActiveOverduePaginated(today, cutoff, 0L, PageRequest.of(0, 500));
    assertThat(results).isEmpty();
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
