package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.lms.entity.*;
import com.example.lms.repository.*;
import com.example.lms.service.ReportService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock BookRepository books;
  @Mock MagazineRepository magazines;
  @Mock NewspaperRepository newspapers;
  @Mock BorrowRecordRepository borrowRecords;
  @Mock StudentProfileRepository students;

  @InjectMocks ReportService service;

  private StudentProfile student;
  private Account account;

  @BeforeEach
  void setUp() {
    account = new Account();
    setId(account, 10L);
    account.setUsername("s1");

    student = new StudentProfile();
    setId(student, 1L);
    student.setAccount(account);
    student.setName("Alice");
    student.setEmail("alice@example.com");
    student.setPhone("555-0100");
  }

  // ==================== inventoryCsv ====================

  @Test
  void inventoryCsvHeaders() {
    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(1, lines.length, "Should only have header row");
    assertEquals("Type,ID,Title,Author/Publisher,Category,ISBN,Date,Available", lines[0]);
  }

  @Test
  void inventoryCsvEmptyInventory() {
    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    assertEquals(1, csv.split("\n").length);
  }

  @Test
  void inventoryCsvBookRow() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Clean Code");
    book.setAuthor("Robert Martin");
    book.setCategory("Software");
    book.setIsbn("9780132350884");
    book.setPublishedDate(LocalDate.of(2008, 8, 1));
    book.setAvailable(true);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(book));
    when(books.findByIdGreaterThan(eq(1L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("Book,1,Clean Code,Robert Martin,Software,9780132350884,2008-08-01,Yes", lines[1]);
  }

  @Test
  void inventoryCsvBookWithNullCategoryAndDate() {
    Book book = new Book();
    setId(book, 5L);
    book.setTitle("No Meta Book");
    book.setAuthor(null);
    book.setCategory(null);
    book.setIsbn("1234567890");
    book.setPublishedDate(null);
    book.setAvailable(false);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(book));
    when(books.findByIdGreaterThan(eq(5L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("Book,5,No Meta Book,,,1234567890,,No", lines[1]);
  }

  @Test
  void inventoryCsvMagazineRow() {
    Magazine mag = new Magazine();
    setId(mag, 3L);
    mag.setTitle("Time");
    mag.setPublisher("Time Inc.");
    mag.setCategory("News");
    mag.setIssueDate(LocalDate.of(2026, 1, 15));
    mag.setAvailable(true);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(mag));
    when(magazines.findByIdGreaterThan(eq(3L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("Magazine,3,Time,Time Inc.,News,,2026-01-15,Yes", lines[1]);
  }

  @Test
  void inventoryCsvNewspaperRow() {
    Newspaper np = new Newspaper();
    setId(np, 7L);
    np.setTitle("Daily Times");
    np.setPublisher("Times Corp");
    np.setPublicationDate(LocalDate.of(2026, 6, 1));
    np.setAvailable(false);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(np));
    when(newspapers.findByIdGreaterThan(eq(7L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("Newspaper,7,Daily Times,Times Corp,,,2026-06-01,No", lines[1]);
  }

  // ==================== borrowingCsv ====================

  @Test
  void borrowingCsvHeaders() {
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    String[] lines = csv.split("\n");
    assertEquals(1, lines.length);
    assertEquals(
        "ID,Item Title,Item Type,Borrower Name,Borrower Email,Borrower Phone,Borrow Date,Due Date,Return Date,Status",
        lines[0]);
  }

  @Test
  void borrowingCsvBookRow() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Test Book");

    BorrowRecord r = new BorrowRecord();
    setId(r, 10L);
    r.setBook(book);
    r.setBorrowerName("Alice");
    r.setBorrowerEmail("alice@ex.com");
    r.setBorrowerPhone("555");
    r.setBorrowDate(LocalDate.of(2026, 9, 1));
    r.setDueDate(LocalDate.of(2026, 9, 15));
    r.setStudent(student);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r));
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(10L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals(
        "10,Test Book,BOOK,Alice,alice@ex.com,555,2026-09-01,2026-09-15,,BORROWED", lines[1]);
  }

  @Test
  void borrowingCsvWithReturnDate() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Returned Book");

    BorrowRecord r = new BorrowRecord();
    setId(r, 20L);
    r.setBook(book);
    r.setBorrowerName("Bob");
    r.setBorrowerEmail("bob@ex.com");
    r.setBorrowerPhone("556");
    r.setBorrowDate(LocalDate.of(2026, 8, 1));
    r.setDueDate(LocalDate.of(2026, 8, 15));
    r.setReturnDate(LocalDate.of(2026, 8, 10));
    r.setStudent(student);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r));
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(20L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals(
        "20,Returned Book,BOOK,Bob,bob@ex.com,556,2026-08-01,2026-08-15,2026-08-10,RETURNED",
        lines[1]);
  }

  @Test
  void borrowingCsvDueDateFallbackToBorrowDatePlus14() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("No Due Book");

    BorrowRecord r = new BorrowRecord();
    setId(r, 30L);
    r.setBook(book);
    r.setBorrowerName("Charlie");
    r.setBorrowerEmail("charlie@ex.com");
    r.setBorrowerPhone("557");
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setDueDate(null);
    r.setStudent(student);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r));
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(30L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(
        lines[1].contains("2026-07-15"),
        "Due date should default to borrowDate+14, got: " + lines[1]);
  }

  @Test
  void borrowingCsvNullDateRangeFetchesAll() {
    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.borrowingCsv(null, null);
    String[] lines = csv.split("\n");
    assertEquals(1, lines.length, "Should have header only with empty data");
  }

  @Test
  void borrowingCsvMagazineType() {
    Magazine mag = new Magazine();
    setId(mag, 2L);
    mag.setTitle("Science Mag");

    BorrowRecord r = new BorrowRecord();
    setId(r, 40L);
    r.setMagazine(mag);
    r.setBorrowerName("Dana");
    r.setBorrowerEmail("dana@ex.com");
    r.setBorrowerPhone("558");
    r.setBorrowDate(LocalDate.of(2026, 6, 1));
    r.setDueDate(LocalDate.of(2026, 6, 15));
    r.setStudent(student);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r));
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(40L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(lines[1].contains("MAGAZINE"));
    assertTrue(lines[1].contains("Science Mag"));
  }

  @Test
  void borrowingCsvNewspaperType() {
    Newspaper np = new Newspaper();
    setId(np, 3L);
    np.setTitle("Daily Herald");

    BorrowRecord r = new BorrowRecord();
    setId(r, 50L);
    r.setNewspaper(np);
    r.setBorrowerName("Eve");
    r.setBorrowerEmail("eve@ex.com");
    r.setBorrowerPhone("559");
    r.setBorrowDate(LocalDate.of(2026, 5, 1));
    r.setDueDate(LocalDate.of(2026, 5, 15));
    r.setStudent(student);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r));
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(50L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(lines[1].contains("NEWSPAPER"));
    assertTrue(lines[1].contains("Daily Herald"));
  }

  // ==================== studentsCsv ====================

  @Test
  void studentsCsvHeaders() {
    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(students.findByIdGreaterThanWithAccount(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.studentsCsv();
    String[] lines = csv.split("\n");
    assertEquals(1, lines.length);
    assertEquals("ID,Name,Email,Phone,Username,Total Borrows,Active Borrows", lines[0]);
  }

  @Test
  void studentsCsvWithBorrowCounts() {
    BorrowRecord r1 = new BorrowRecord();
    setId(r1, 1L);
    r1.setStudent(student);
    r1.setReturnDate(null);
    BorrowRecord r2 = new BorrowRecord();
    setId(r2, 2L);
    r2.setStudent(student);
    r2.setReturnDate(LocalDate.of(2026, 8, 1));

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r1, r2));
    when(borrowRecords.findByIdGreaterThan(eq(2L), any(PageRequest.class))).thenReturn(List.of());
    when(students.findByIdGreaterThanWithAccount(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(student));
    when(students.findByIdGreaterThanWithAccount(eq(1L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.studentsCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("1,Alice,alice@example.com,555-0100,s1,2,1", lines[1]);
  }

  @Test
  void studentsCsvStudentWithNoBorrows() {
    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(students.findByIdGreaterThanWithAccount(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(student));
    when(students.findByIdGreaterThanWithAccount(eq(1L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.studentsCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("1,Alice,alice@example.com,555-0100,s1,0,0", lines[1]);
  }

  // ==================== overdueCsv ====================

  @Test
  void overdueCsvHeaders() {
    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.overdueCsv();
    String[] lines = csv.split("\n");
    assertEquals(1, lines.length);
    assertEquals("ID,Item Title,Borrower Name,Borrow Date,Days Overdue", lines[0]);
  }

  @Test
  void overdueCsvFiltersCorrectly() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Overdue Book");
    BorrowRecord overdue = new BorrowRecord();
    setId(overdue, 10L);
    overdue.setBook(book);
    overdue.setBorrowerName("Alice");
    overdue.setBorrowDate(LocalDate.of(2026, 7, 1));
    overdue.setDueDate(LocalDate.now().minusDays(5));
    overdue.setStudent(student);

    BorrowRecord notOverdue = new BorrowRecord();
    setId(notOverdue, 20L);
    notOverdue.setBook(book);
    notOverdue.setBorrowerName("Bob");
    notOverdue.setBorrowDate(LocalDate.of(2026, 9, 1));
    notOverdue.setDueDate(LocalDate.now().plusDays(5));
    notOverdue.setStudent(student);

    BorrowRecord returned = new BorrowRecord();
    setId(returned, 30L);
    returned.setBook(book);
    returned.setBorrowerName("Charlie");
    returned.setBorrowDate(LocalDate.of(2026, 6, 1));
    returned.setDueDate(LocalDate.of(2026, 6, 15));
    returned.setReturnDate(LocalDate.of(2026, 6, 10));
    returned.setStudent(student);

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(overdue, notOverdue, returned));
    when(borrowRecords.findByIdGreaterThan(eq(30L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.overdueCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length, "Should have header + 1 overdue row");
    assertTrue(lines[1].contains("Overdue Book"));
    assertTrue(lines[1].contains("Alice"));
    assertTrue(
        lines[1].endsWith(",5"),
        "Days overdue should be the last field with value 5, got: " + lines[1]);
  }

  @Test
  void overdueCsvEmptyWhenNoOverdue() {
    BorrowRecord notOverdue = new BorrowRecord();
    setId(notOverdue, 1L);
    notOverdue.setBook(new Book());
    notOverdue.setBorrowerName("Alice");
    notOverdue.setBorrowDate(LocalDate.of(2026, 9, 1));
    notOverdue.setDueDate(LocalDate.now().plusDays(5));
    notOverdue.setStudent(student);

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(notOverdue));
    when(borrowRecords.findByIdGreaterThan(eq(1L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.overdueCsv();
    assertEquals(1, csv.split("\n").length, "Should have header only");
  }

  @Test
  void overdueCsvExcludesReturnedRecords() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Calc Check Book");
    BorrowRecord r = new BorrowRecord();
    setId(r, 10L);
    r.setBook(book);
    r.setBorrowerName("Alice");
    r.setBorrowDate(LocalDate.of(2026, 7, 1));
    r.setDueDate(LocalDate.of(2026, 7, 10));
    r.setReturnDate(LocalDate.of(2026, 7, 20));
    r.setStudent(student);

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(r));
    when(borrowRecords.findByIdGreaterThan(eq(10L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.overdueCsv();
    String[] lines = csv.split("\n");
    assertEquals(1, lines.length, "Returned record should not appear in overdue CSV");
  }

  @Test
  void overdueCsvMagazineTitle() {
    Magazine mag = new Magazine();
    setId(mag, 2L);
    mag.setTitle("Overdue Mag");
    BorrowRecord r = new BorrowRecord();
    setId(r, 15L);
    r.setMagazine(mag);
    r.setBorrowerName("Bob");
    r.setBorrowDate(LocalDate.of(2026, 6, 1));
    r.setDueDate(LocalDate.now().minusDays(3));
    r.setStudent(student);

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(r));
    when(borrowRecords.findByIdGreaterThan(eq(15L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.overdueCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(lines[1].contains("Overdue Mag"));
  }

  @Test
  void overdueCsvNewspaperTitle() {
    Newspaper np = new Newspaper();
    setId(np, 3L);
    np.setTitle("Overdue Paper");
    BorrowRecord r = new BorrowRecord();
    setId(r, 16L);
    r.setNewspaper(np);
    r.setBorrowerName("Charlie");
    r.setBorrowDate(LocalDate.of(2026, 5, 1));
    r.setDueDate(LocalDate.now().minusDays(10));
    r.setStudent(student);

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(r));
    when(borrowRecords.findByIdGreaterThan(eq(16L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.overdueCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(lines[1].contains("Overdue Paper"));
  }

  // ==================== CSV escaping ====================

  @Test
  void csvEscapesCommasInTitle() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Code, Software, and Design");
    book.setAuthor("Author");
    book.setCategory("Cat");
    book.setIsbn("123");
    book.setAvailable(true);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(book));
    when(books.findByIdGreaterThan(eq(1L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(lines[1].contains("\"Code, Software, and Design\""), "Commas should be quoted");
  }

  @Test
  void csvEscapesQuotesInTitle() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("The \"Pragmatic\" Programmer");
    book.setAuthor("Author");
    book.setAvailable(true);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(book));
    when(books.findByIdGreaterThan(eq(1L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertTrue(
        lines[1].contains("\"The \"\"Pragmatic\"\" Programmer\""),
        "Quotes should be escaped by doubling");
  }

  @Test
  void csvEscapesNewlinesInBorrowerName() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Book");

    BorrowRecord r = new BorrowRecord();
    setId(r, 10L);
    r.setBook(book);
    r.setBorrowerName("Line1\nLine2");
    r.setBorrowerEmail("a@b.com");
    r.setBorrowerPhone("555");
    r.setBorrowDate(LocalDate.of(2026, 9, 1));
    r.setDueDate(LocalDate.of(2026, 9, 15));
    r.setStudent(student);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(r));
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(10L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    int quoteCount = 0;
    for (int i = 0; i < csv.length(); i++) {
      if (csv.charAt(i) == '"') quoteCount++;
    }
    assertTrue(
        quoteCount >= 2,
        "Newline value should be wrapped in quotes, found " + quoteCount + " quote chars");
    assertTrue(csv.contains("Line1"), "Should contain Line1");
    assertTrue(csv.contains("Line2"), "Should contain Line2");
  }

  @Test
  void csvNullValuesBecomeEmpty() {
    Book book = new Book();
    setId(book, 1L);
    book.setTitle("Book");
    book.setAuthor(null);
    book.setCategory(null);
    book.setIsbn(null);
    book.setPublishedDate(null);
    book.setAvailable(true);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of(book));
    when(books.findByIdGreaterThan(eq(1L), any(PageRequest.class))).thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);
    assertEquals("Book,1,Book,,,,,Yes", lines[1]);
  }

  // ==================== Multi-batch pagination ====================

  @Test
  void inventoryCsvCrossesBatchBoundary() {
    int batchSize = 500;
    int extra = 1;
    List<Book> firstBatch = new ArrayList<>();
    for (int i = 1; i <= batchSize; i++) {
      Book b = new Book();
      setId(b, (long) i);
      b.setTitle("Book " + i);
      b.setAvailable(true);
      firstBatch.add(b);
    }
    Book last = new Book();
    setId(last, (long) (batchSize + 1));
    last.setTitle("Book " + (batchSize + 1));
    last.setAvailable(true);
    List<Book> secondBatch = List.of(last);

    when(books.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(firstBatch);
    when(books.findByIdGreaterThan(eq((long) batchSize), any(PageRequest.class)))
        .thenReturn(secondBatch);
    when(books.findByIdGreaterThan(eq((long) (batchSize + 1)), any(PageRequest.class)))
        .thenReturn(List.of());
    when(magazines.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(newspapers.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());

    String csv = service.inventoryCsv();
    String[] lines = csv.split("\n");
    assertEquals(batchSize + extra + 1, lines.length, "Header + 501 book rows");
    assertTrue(lines[1].contains("Book 1"));
    assertTrue(lines[batchSize].contains("Book 500"));
    assertTrue(lines[batchSize + 1].contains("Book 501"));

    ArgumentCaptor<Long> cursorCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(books, times(3)).findByIdGreaterThan(cursorCaptor.capture(), pageCaptor.capture());
    assertEquals(0L, cursorCaptor.getAllValues().get(0));
    assertEquals(500L, cursorCaptor.getAllValues().get(1));
    assertEquals(501L, cursorCaptor.getAllValues().get(2));
    assertEquals(Sort.by("id"), pageCaptor.getValue().getSort());
  }

  @Test
  void borrowingCsvMultipleBatches() {
    int batchSize = 500;
    List<BorrowRecord> firstBatch = new ArrayList<>();
    for (int i = 1; i <= batchSize; i++) {
      BorrowRecord r = new BorrowRecord();
      setId(r, (long) i);
      Book book = new Book();
      setId(book, (long) (1000 + i));
      book.setTitle("Batch Book " + i);
      r.setBook(book);
      r.setBorrowerName("Borrower " + i);
      r.setBorrowerEmail("b" + i + "@ex.com");
      r.setBorrowerPhone("555");
      r.setBorrowDate(LocalDate.of(2026, 9, 1));
      r.setDueDate(LocalDate.of(2026, 9, 15));
      r.setStudent(student);
      firstBatch.add(r);
    }

    BorrowRecord last = new BorrowRecord();
    setId(last, (long) (batchSize + 1));
    Book lastBook = new Book();
    setId(lastBook, (long) (1000 + batchSize + 1));
    lastBook.setTitle("Batch Book " + (batchSize + 1));
    last.setBook(lastBook);
    last.setBorrowerName("Borrower " + (batchSize + 1));
    last.setBorrowerEmail("b" + (batchSize + 1) + "@ex.com");
    last.setBorrowerPhone("555");
    last.setBorrowDate(LocalDate.of(2026, 9, 1));
    last.setDueDate(LocalDate.of(2026, 9, 15));
    last.setStudent(student);
    List<BorrowRecord> secondBatch = List.of(last);

    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class), any(LocalDate.class), eq(0L), any(PageRequest.class)))
        .thenReturn(firstBatch);
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class),
            any(LocalDate.class),
            eq((long) batchSize),
            any(PageRequest.class)))
        .thenReturn(secondBatch);
    when(borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class),
            any(LocalDate.class),
            eq((long) (batchSize + 1)),
            any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.borrowingCsv(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    String[] lines = csv.split("\n");
    assertEquals(batchSize + 2, lines.length, "Header + 501 borrow rows");
    assertTrue(lines[1].contains("Batch Book 1"));
    assertTrue(lines[batchSize].contains("Batch Book 500"));
    assertTrue(lines[batchSize + 1].contains("Batch Book 501"));

    ArgumentCaptor<Long> cursorCaptor = ArgumentCaptor.forClass(Long.class);
    verify(borrowRecords, times(3))
        .findByBorrowDateBetweenAndIdGreaterThan(
            any(LocalDate.class),
            any(LocalDate.class),
            cursorCaptor.capture(),
            any(PageRequest.class));
    assertEquals(0L, cursorCaptor.getAllValues().get(0));
    assertEquals(500L, cursorCaptor.getAllValues().get(1));
    assertEquals(501L, cursorCaptor.getAllValues().get(2));
  }

  // ==================== Defensive null-account handling ====================
  // The current schema enforces account_id NOT NULL with a foreign key on student_profiles,
  // so a StudentProfile with a null Account cannot arise from a real DB query.
  // This test exercises the null-guard in studentsCsv() as defense-in-depth only.

  @Test
  void studentsCsvNullAccountGuardReturnsEmptyUsername() {
    StudentProfile impossible = new StudentProfile();
    setId(impossible, 99L);
    impossible.setAccount(null);
    impossible.setName("Impossible Student");
    impossible.setEmail("impossible@example.com");
    impossible.setPhone("000");

    when(borrowRecords.findByIdGreaterThan(eq(0L), any(PageRequest.class))).thenReturn(List.of());
    when(students.findByIdGreaterThanWithAccount(eq(0L), any(PageRequest.class)))
        .thenReturn(List.of(impossible));
    when(students.findByIdGreaterThanWithAccount(eq(99L), any(PageRequest.class)))
        .thenReturn(List.of());

    String csv = service.studentsCsv();
    String[] lines = csv.split("\n");
    assertEquals(2, lines.length, "Student should appear in CSV even with null account");
    assertEquals("99,Impossible Student,impossible@example.com,000,,0,0", lines[1]);
  }

  private static void setId(Object entity, Long id) {
    try {
      Field field = entity.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set id via reflection", e);
    }
  }
}
