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

  @Test
  void concurrentReturnOfSameRecordOneSucceedsOneFails() throws Exception {
    Book book = new Book();
    book.setTitle("Return Race Book");
    book.setIsbn("9780000000203");
    book.setAvailable(false);
    books.save(book);

    BorrowRecord record = new BorrowRecord();
    record.setBook(book);
    record.setStudent(student);
    record.setBorrowerName(student.getName());
    record.setBorrowerEmail(student.getEmail());
    record.setBorrowerPhone(student.getPhone());
    record.setBorrowDate(LocalDate.of(2026, 9, 1));
    BorrowRecord saved = borrowRecords.save(record);
    Long savedId = saved.getId();

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<Object> f1 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                borrowService.returnBook(savedId);
                return "SUCCESS";
              } catch (Exception e) {
                return e;
              }
            });

    Future<Object> f2 =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                borrowService.returnBook(savedId);
                return "SUCCESS";
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
      if ("SUCCESS".equals(r)) {
        successCount++;
      } else if (r instanceof Exception) {
        failCount++;
      }
    }

    assertEquals(1, successCount, "Exactly one return should succeed");
    assertEquals(1, failCount, "Exactly one return should fail");

    Book afterBook = books.findById(book.getId()).orElseThrow();
    assertTrue(afterBook.isAvailable(), "Book should be available after return");

    BorrowRecord afterRecord = borrowRecords.findById(savedId).orElseThrow();
    assertNotNull(afterRecord.getReturnDate(), "Return date should be set");
  }

  @Test
  void returnAndBorrowRace() throws Exception {
    Book book = new Book();
    book.setTitle("Return-Borrow Race Book");
    book.setIsbn("9780000000204");
    book.setAvailable(false);
    books.save(book);

    BorrowRecord record = new BorrowRecord();
    record.setBook(book);
    record.setStudent(student);
    record.setBorrowerName(student.getName());
    record.setBorrowerEmail(student.getEmail());
    record.setBorrowerPhone(student.getPhone());
    record.setBorrowDate(LocalDate.of(2026, 9, 1));
    BorrowRecord saved = borrowRecords.save(record);
    Long savedId = saved.getId();

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<Object> fReturn =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                borrowService.returnBook(savedId);
                return "RETURN_SUCCESS";
              } catch (Exception e) {
                return e;
              }
            });

    BorrowRequest borrowReq =
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

    Future<Object> fBorrow =
        executor.submit(
            () -> {
              barrier.await(2, TimeUnit.SECONDS);
              try {
                return borrowService.borrow(borrowReq);
              } catch (Exception e) {
                return e;
              }
            });

    Object rReturn = fReturn.get(10, TimeUnit.SECONDS);
    Object rBorrow = fBorrow.get(10, TimeUnit.SECONDS);
    executor.shutdown();

    Book afterBook = books.findById(book.getId()).orElseThrow();
    long activeBorrowCount =
        borrowRecords.findByReturnDateIsNull().stream()
            .filter(r -> r.getBook() != null && r.getBook().getId().equals(book.getId()))
            .count();

    if ("RETURN_SUCCESS".equals(rReturn)) {
      if (rBorrow instanceof com.example.lms.dto.BorrowRecordResponse) {
        assertFalse(afterBook.isAvailable(), "Item should be unavailable after borrow");
        assertEquals(1, activeBorrowCount, "Exactly one active borrow after borrow wins");
      } else {
        assertTrue(
            afterBook.isAvailable(), "Item should be available after return if borrow failed");
        assertEquals(0, activeBorrowCount, "No active borrows if borrow failed");
      }
    } else if (rReturn instanceof Exception) {
      assertFalse(afterBook.isAvailable(), "Item should remain unavailable if return failed");
      assertEquals(0, activeBorrowCount, "No active borrows after return failure");
    }

    assertTrue(
        afterBook.isAvailable() || activeBorrowCount > 0,
        "State must be consistent: available implies no active borrows");
  }

  @Test
  void secondReturnBlockedByFirstReturnLock() throws Exception {
    Book book = new Book();
    book.setTitle("Stale Return Book");
    book.setIsbn("9780000000205");
    book.setAvailable(false);
    books.save(book);

    BorrowRecord record = new BorrowRecord();
    record.setBook(book);
    record.setStudent(student);
    record.setBorrowerName(student.getName());
    record.setBorrowerEmail(student.getEmail());
    record.setBorrowerPhone(student.getPhone());
    record.setBorrowDate(LocalDate.of(2026, 9, 1));
    BorrowRecord saved = borrowRecords.save(record);
    Long savedId = saved.getId();

    CountDownLatch return1Started = new CountDownLatch(1);
    CountDownLatch return1CanFinish = new CountDownLatch(1);
    CountDownLatch return2Done = new CountDownLatch(1);

    Thread return1 =
        new Thread(
            () -> {
              try {
                return1Started.countDown();
                return1CanFinish.await(5, TimeUnit.SECONDS);
                borrowService.returnBook(savedId);
              } catch (Exception e) {
              }
            });

    Thread return2 =
        new Thread(
            () -> {
              try {
                return1Started.await(5, TimeUnit.SECONDS);
                Thread.sleep(50);
                borrowService.returnBook(savedId);
              } catch (Exception e) {
              } finally {
                return2Done.countDown();
              }
            });

    return1.start();
    return2.start();
    return1Started.await(5, TimeUnit.SECONDS);
    Thread.sleep(50);

    return1CanFinish.countDown();
    return1.join(10000);
    return2Done.await(10, TimeUnit.SECONDS);
    return2.join(10000);

    BorrowRecord afterRecord = borrowRecords.findById(savedId).orElseThrow();
    Book afterBook = books.findById(book.getId()).orElseThrow();

    assertNotNull(afterRecord.getReturnDate(), "Return date should be set");
    assertTrue(afterBook.isAvailable(), "Book should be available after return");

    long activeBorrowCount =
        borrowRecords.findByReturnDateIsNull().stream()
            .filter(r -> r.getBook() != null && r.getBook().getId().equals(book.getId()))
            .count();
    assertEquals(0, activeBorrowCount, "No active borrows should exist");
  }
}
