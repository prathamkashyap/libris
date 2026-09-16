package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.example.lms.dto.BorrowRequest;
import com.example.lms.entity.*;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.repository.*;
import com.example.lms.service.BorrowRecordService;
import java.time.LocalDate;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class BorrowConcurrencyTest {

  @Autowired BorrowRecordService borrowService;
  @Autowired BookRepository books;
  @Autowired MagazineRepository magazines;
  @Autowired NewspaperRepository newspapers;
  @Autowired BorrowRecordRepository borrowRecords;
  @Autowired StudentProfileRepository students;
  @Autowired AccountRepository accounts;

  private StudentProfile student;

  @BeforeEach
  void setUp() {
    Account account = new Account();
    account.setUsername("conc-student-" + System.nanoTime());
    account.setPasswordHash("hash");
    account.setRole(Role.STUDENT);
    account = accounts.save(account);

    student = new StudentProfile();
    student.setAccount(account);
    student.setName("Conc Student");
    student.setEmail("conc-" + System.nanoTime() + "@test.com");
    student.setPhone("555");
    student = students.save(student);
  }

  @AfterEach
  void cleanup() {
    borrowRecords.deleteAll();
    books.deleteAll();
    magazines.deleteAll();
    newspapers.deleteAll();
    students.deleteAll();
    accounts.deleteAll();
  }

  @Test
  void concurrentBookBorrowOneSucceedsOneFails() throws Exception {
    Book book = new Book();
    book.setTitle("Race Book");
    book.setIsbn("9780000000201");
    book.setAvailable(true);
    books.save(book);

    BorrowRequest req =
        new BorrowRequest(
            book.getId(),
            null,
            null,
            student.getId(),
            "Conc Student",
            "conc@test.com",
            "555",
            LocalDate.of(2026, 9, 16),
            null);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<Object> f1 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(req);
              } catch (Exception e) {
                return e;
              }
            });

    Future<Object> f2 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(req);
              } catch (Exception e) {
                return e;
              }
            });

    Object r1 = f1.get(10, TimeUnit.SECONDS);
    Object r2 = f2.get(10, TimeUnit.SECONDS);
    executor.shutdown();

    int successCount = 0;
    int failCount = 0;
    for (Object r : new Object[] {r1, r2}) {
      if (r instanceof com.example.lms.dto.BorrowRecordResponse) {
        successCount++;
      } else if (r instanceof Exception) {
        failCount++;
      }
    }

    assertEquals(1, successCount, "Exactly one borrow should succeed");
    assertEquals(1, failCount, "Exactly one borrow should fail");

    Book after = books.findById(book.getId()).orElseThrow();
    assertFalse(after.isAvailable(), "Book should be unavailable after borrow");
    assertEquals(1, borrowRecords.count(), "Exactly one active borrow record");
  }

  @Test
  void concurrentMagazineBorrowOneSucceedsOneFails() throws Exception {
    Magazine mag = new Magazine();
    mag.setTitle("Race Magazine");
    mag.setAvailable(true);
    magazines.save(mag);

    BorrowRequest req =
        new BorrowRequest(
            null,
            mag.getId(),
            null,
            student.getId(),
            "Conc Student",
            "conc@test.com",
            "555",
            LocalDate.of(2026, 9, 16),
            null);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<Object> f1 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(req);
              } catch (Exception e) {
                return e;
              }
            });

    Future<Object> f2 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(req);
              } catch (Exception e) {
                return e;
              }
            });

    Object r1 = f1.get(10, TimeUnit.SECONDS);
    Object r2 = f2.get(10, TimeUnit.SECONDS);
    executor.shutdown();

    int successCount = 0;
    int failCount = 0;
    for (Object r : new Object[] {r1, r2}) {
      if (r instanceof com.example.lms.dto.BorrowRecordResponse) {
        successCount++;
      } else if (r instanceof Exception) {
        failCount++;
      }
    }

    assertEquals(1, successCount, "Exactly one borrow should succeed");
    assertEquals(1, failCount, "Exactly one borrow should fail");

    Magazine after = magazines.findById(mag.getId()).orElseThrow();
    assertFalse(after.isAvailable(), "Magazine should be unavailable after borrow");
    assertEquals(1, borrowRecords.count(), "Exactly one active borrow record");
  }

  @Test
  void concurrentNewspaperBorrowOneSucceedsOneFails() throws Exception {
    Newspaper np = new Newspaper();
    np.setTitle("Race Newspaper");
    np.setAvailable(true);
    newspapers.save(np);

    BorrowRequest req =
        new BorrowRequest(
            null,
            null,
            np.getId(),
            student.getId(),
            "Conc Student",
            "conc@test.com",
            "555",
            LocalDate.of(2026, 9, 16),
            null);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<Object> f1 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(req);
              } catch (Exception e) {
                return e;
              }
            });

    Future<Object> f2 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(req);
              } catch (Exception e) {
                return e;
              }
            });

    Object r1 = f1.get(10, TimeUnit.SECONDS);
    Object r2 = f2.get(10, TimeUnit.SECONDS);
    executor.shutdown();

    int successCount = 0;
    int failCount = 0;
    for (Object r : new Object[] {r1, r2}) {
      if (r instanceof com.example.lms.dto.BorrowRecordResponse) {
        successCount++;
      } else if (r instanceof Exception) {
        failCount++;
      }
    }

    assertEquals(1, successCount, "Exactly one borrow should succeed");
    assertEquals(1, failCount, "Exactly one borrow should fail");

    Newspaper after = newspapers.findById(np.getId()).orElseThrow();
    assertFalse(after.isAvailable(), "Newspaper should be unavailable after borrow");
    assertEquals(1, borrowRecords.count(), "Exactly one active borrow record");
  }

  @Test
  void borrowUnavailableItemRejected() {
    Book book = new Book();
    book.setTitle("Unavailable Book");
    book.setIsbn("9780000000202");
    book.setAvailable(false);
    books.save(book);

    BorrowRequest req =
        new BorrowRequest(
            book.getId(),
            null,
            null,
            student.getId(),
            "Conc Student",
            "conc@test.com",
            "555",
            LocalDate.of(2026, 9, 16),
            null);

    BusinessRuleException ex =
        assertThrows(BusinessRuleException.class, () -> borrowService.borrow(req));
    assertEquals("UNAVAILABLE", ex.getCode());
  }
}
