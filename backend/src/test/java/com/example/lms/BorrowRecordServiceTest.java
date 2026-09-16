package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.lms.dto.BorrowRecordResponse;
import com.example.lms.dto.BorrowRequest;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.*;
import com.example.lms.service.BorrowRecordService;
import com.example.lms.util.CurrentUser;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BorrowRecordServiceTest {

  @Mock BorrowRecordRepository records;
  @Mock BookRepository books;
  @Mock MagazineRepository magazines;
  @Mock NewspaperRepository newspapers;
  @Mock StudentProfileRepository students;
  @Mock ApplicationEventPublisher events;
  @Mock CurrentUser currentUser;

  @InjectMocks BorrowRecordService service;

  private StudentProfile student;
  private Account studentAccount;

  @BeforeEach
  void setUp() {
    studentAccount = new Account();
    setId(studentAccount, 10L);
    studentAccount.setUsername("student1");
    studentAccount.setRole(Role.STUDENT);

    student = new StudentProfile();
    setId(student, 1L);
    student.setAccount(studentAccount);
    student.setName("Alice Student");
    student.setEmail("alice@example.com");
    student.setPhone("555-0100");

    CurrentUser.Actor actor =
        new CurrentUser.Actor(10L, "admin", "ADMIN", "127.0.0.1", "test-agent");
    lenient().when(currentUser.get()).thenReturn(actor);
  }

  @Test
  void borrowBookSuccessfully() {
    Book book = makeBook(1L, "Clean Code", true);
    when(books.findByIdForBorrow(1L)).thenReturn(Optional.of(book));
    when(students.findById(1L)).thenReturn(Optional.of(student));
    when(records.save(any(BorrowRecord.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, BorrowRecord.class), 100L);
              return inv.getArgument(0);
            });

    BorrowRequest req =
        new BorrowRequest(
            1L, null, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BorrowRecordResponse resp = service.borrow(req);

    assertFalse(book.isAvailable(), "Book should be marked unavailable after borrow");
    assertEquals("Clean Code", resp.itemTitle());
    assertEquals("BOOK", resp.itemType());
    assertEquals(1L, resp.studentId());
    assertEquals(LocalDate.of(2026, 9, 1), resp.borrowDate());
    assertEquals(
        LocalDate.of(2026, 9, 15), resp.dueDate(), "Null dueDate should default to borrowDate+14");
    assertNull(resp.returnDate());
    assertEquals("BORROWED", resp.status());
  }

  @Test
  void borrowMagazineSuccessfully() {
    Magazine magazine = makeMagazine(2L, "Time", true);
    when(magazines.findByIdForBorrow(2L)).thenReturn(Optional.of(magazine));
    when(students.findById(1L)).thenReturn(Optional.of(student));
    when(records.save(any(BorrowRecord.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, BorrowRecord.class), 101L);
              return inv.getArgument(0);
            });

    BorrowRequest req =
        new BorrowRequest(
            null, 2L, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BorrowRecordResponse resp = service.borrow(req);

    assertFalse(magazine.isAvailable());
    assertEquals("MAGAZINE", resp.itemType());
    assertEquals("Time", resp.itemTitle());
  }

  @Test
  void borrowNewspaperSuccessfully() {
    Newspaper newspaper = makeNewspaper(3L, "Daily News", true);
    when(newspapers.findByIdForBorrow(3L)).thenReturn(Optional.of(newspaper));
    when(students.findById(1L)).thenReturn(Optional.of(student));
    when(records.save(any(BorrowRecord.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, BorrowRecord.class), 102L);
              return inv.getArgument(0);
            });

    BorrowRequest req =
        new BorrowRequest(
            null, null, 3L, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BorrowRecordResponse resp = service.borrow(req);

    assertFalse(newspaper.isAvailable());
    assertEquals("NEWSPAPER", resp.itemType());
    assertEquals("Daily News", resp.itemTitle());
  }

  @Test
  void borrowUnavailableBookThrows() {
    Book book = makeBook(1L, "Gone Book", false);
    when(books.findByIdForBorrow(1L)).thenReturn(Optional.of(book));
    when(students.findById(1L)).thenReturn(Optional.of(student));

    BorrowRequest req =
        new BorrowRequest(
            1L, null, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.borrow(req));
    assertEquals("UNAVAILABLE", ex.getCode());
  }

  @Test
  void borrowUnavailableMagazineThrows() {
    Magazine magazine = makeMagazine(2L, "Gone Mag", false);
    when(magazines.findByIdForBorrow(2L)).thenReturn(Optional.of(magazine));
    when(students.findById(1L)).thenReturn(Optional.of(student));

    BorrowRequest req =
        new BorrowRequest(
            null, 2L, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.borrow(req));
    assertEquals("UNAVAILABLE", ex.getCode());
  }

  @Test
  void borrowUnavailableNewspaperThrows() {
    Newspaper newspaper = makeNewspaper(3L, "Gone Paper", false);
    when(newspapers.findByIdForBorrow(3L)).thenReturn(Optional.of(newspaper));
    when(students.findById(1L)).thenReturn(Optional.of(student));

    BorrowRequest req =
        new BorrowRequest(
            null, null, 3L, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.borrow(req));
    assertEquals("UNAVAILABLE", ex.getCode());
  }

  @Test
  void borrowWithExplicitDueDate() {
    Book book = makeBook(1L, "Book", true);
    when(books.findByIdForBorrow(1L)).thenReturn(Optional.of(book));
    when(students.findById(1L)).thenReturn(Optional.of(student));
    when(records.save(any(BorrowRecord.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, BorrowRecord.class), 103L);
              return inv.getArgument(0);
            });

    BorrowRequest req =
        new BorrowRequest(
            1L,
            null,
            null,
            1L,
            "Alice",
            "alice@ex.com",
            "555",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 1));
    BorrowRecordResponse resp = service.borrow(req);

    assertEquals(LocalDate.of(2026, 10, 1), resp.dueDate(), "Explicit dueDate should be preserved");
  }

  @Test
  void borrowMultipleItemsThrows() {
    BorrowRequest req =
        new BorrowRequest(
            1L, 2L, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.borrow(req));
    assertEquals("INVALID_REQUEST", ex.getCode());
  }

  @Test
  void borrowNoItemsThrows() {
    BorrowRequest req =
        new BorrowRequest(
            null, null, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.borrow(req));
    assertEquals("INVALID_REQUEST", ex.getCode());
  }

  @Test
  void borrowWithUnknownStudentThrows() {
    when(students.findById(999L)).thenReturn(Optional.empty());

    BorrowRequest req =
        new BorrowRequest(
            1L, null, null, 999L, "Nobody", "n@x.com", "000", LocalDate.of(2026, 9, 1), null);
    assertThrows(ResourceNotFoundException.class, () -> service.borrow(req));
  }

  @Test
  void borrowWithUnknownBookThrows() {
    when(books.findByIdForBorrow(999L)).thenReturn(Optional.empty());
    when(students.findById(1L)).thenReturn(Optional.of(student));

    BorrowRequest req =
        new BorrowRequest(
            999L, null, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    assertThrows(ResourceNotFoundException.class, () -> service.borrow(req));
  }

  @Test
  void borrowCopiesStudentInfoToRecord() {
    Book book = makeBook(1L, "Book", true);
    when(books.findByIdForBorrow(1L)).thenReturn(Optional.of(book));
    when(students.findById(1L)).thenReturn(Optional.of(student));
    ArgumentCaptor<BorrowRecord> captor = ArgumentCaptor.forClass(BorrowRecord.class);
    when(records.save(captor.capture()))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, BorrowRecord.class), 104L);
              return inv.getArgument(0);
            });

    BorrowRequest req =
        new BorrowRequest(
            1L, null, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    service.borrow(req);

    BorrowRecord saved = captor.getValue();
    assertEquals("Alice Student", saved.getBorrowerName());
    assertEquals("alice@example.com", saved.getBorrowerEmail());
    assertEquals("555-0100", saved.getBorrowerPhone());
    assertEquals(student, saved.getStudent());
  }

  @Test
  void borrowPublishesAuditEvent() {
    Book book = makeBook(1L, "Test Book", true);
    when(books.findByIdForBorrow(1L)).thenReturn(Optional.of(book));
    when(students.findById(1L)).thenReturn(Optional.of(student));
    when(records.save(any(BorrowRecord.class)))
        .thenAnswer(
            inv -> {
              setId(inv.getArgument(0, BorrowRecord.class), 105L);
              return inv.getArgument(0);
            });

    BorrowRequest req =
        new BorrowRequest(
            1L, null, null, 1L, "Alice", "alice@ex.com", "555", LocalDate.of(2026, 9, 1), null);
    service.borrow(req);

    ArgumentCaptor<EntityAuditEvent> eventCaptor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    EntityAuditEvent event = eventCaptor.getValue();
    assertEquals(AuditAction.BORROW, event.getAction());
    assertEquals(AuditEntityType.BORROW_RECORD, event.getEntityType());
    assertEquals(105L, event.getEntityId());
    assertTrue(event.getDescription().contains("Test Book"));
    assertTrue(event.getDescription().contains("Alice Student"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void returnBookSuccessfully() {
    Book book = makeBook(1L, "Returnable Book", false);
    BorrowRecord record = makeBorrowRecord(50L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findById(50L)).thenReturn(Optional.of(record));

    service.returnBook(50L);

    assertTrue(book.isAvailable(), "Book should be available after return");
    assertNotNull(record.getReturnDate(), "Return date should be set");
  }

  @Test
  void returnPublishesAuditEvent() {
    Book book = makeBook(1L, "Return Audit Book", false);
    BorrowRecord record = makeBorrowRecord(51L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findById(51L)).thenReturn(Optional.of(record));

    service.returnBook(51L);

    ArgumentCaptor<EntityAuditEvent> eventCaptor = ArgumentCaptor.forClass(EntityAuditEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    EntityAuditEvent event = eventCaptor.getValue();
    assertEquals(AuditAction.RETURN, event.getAction());
    assertEquals(AuditEntityType.BORROW_RECORD, event.getEntityType());
    assertEquals(51L, event.getEntityId());
    assertTrue(event.getDescription().contains("Return Audit Book"));
    assertEquals(10L, event.getActorId());
    assertEquals("admin", event.getActorUsername());
    assertEquals("ADMIN", event.getActorRole());
    assertEquals("127.0.0.1", event.getIpAddress());
    assertEquals("test-agent", event.getUserAgent());
  }

  @Test
  void returnAlreadyReturnedThrows() {
    Book book = makeBook(1L, "Book", false);
    BorrowRecord record = makeBorrowRecord(52L, book, null, null, LocalDate.of(2026, 9, 1));
    record.setReturnDate(LocalDate.of(2026, 9, 5));
    when(records.findById(52L)).thenReturn(Optional.of(record));

    BusinessRuleException ex =
        assertThrows(BusinessRuleException.class, () -> service.returnBook(52L));
    assertEquals("ALREADY_RETURNED", ex.getCode());
  }

  @Test
  void returnNonexistentRecordThrows() {
    when(records.findById(999L)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> service.returnBook(999L));
  }

  @Test
  void returnMagazineMakesItAvailable() {
    Magazine magazine = makeMagazine(2L, "Mag", false);
    BorrowRecord record = makeBorrowRecord(53L, null, magazine, null, LocalDate.of(2026, 9, 1));
    when(records.findById(53L)).thenReturn(Optional.of(record));

    service.returnBook(53L);

    assertTrue(magazine.isAvailable());
  }

  @Test
  void returnNewspaperMakesItAvailable() {
    Newspaper newspaper = makeNewspaper(3L, "Paper", false);
    BorrowRecord record = makeBorrowRecord(54L, null, null, newspaper, LocalDate.of(2026, 9, 1));
    when(records.findById(54L)).thenReturn(Optional.of(record));

    service.returnBook(54L);

    assertTrue(newspaper.isAvailable());
  }

  @Test
  void responseMapsBookFields() {
    Book book = makeBook(1L, "Mapped Book", true);
    BorrowRecord record = makeBorrowRecord(60L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findByStudentId(eq(1L), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
    var page =
        service.listByStudentId(1L, null, org.springframework.data.domain.PageRequest.of(0, 10));
    BorrowRecordResponse r = page.getContent().get(0);

    assertEquals(60L, r.id());
    assertEquals(1L, r.itemId());
    assertEquals("Mapped Book", r.itemTitle());
    assertEquals("BOOK", r.itemType());
    assertEquals("BORROWED", r.status());
  }

  @Test
  void responseMapsMagazineFields() {
    Magazine magazine = makeMagazine(2L, "Mapped Mag", true);
    BorrowRecord record = makeBorrowRecord(61L, null, magazine, null, LocalDate.of(2026, 9, 1));
    when(records.findByStudentId(eq(1L), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
    var page =
        service.listByStudentId(1L, null, org.springframework.data.domain.PageRequest.of(0, 10));
    BorrowRecordResponse r = page.getContent().get(0);

    assertEquals(2L, r.itemId());
    assertEquals("Mapped Mag", r.itemTitle());
    assertEquals("MAGAZINE", r.itemType());
  }

  @Test
  void responseMapsNewspaperFields() {
    Newspaper newspaper = makeNewspaper(3L, "Mapped Paper", true);
    BorrowRecord record = makeBorrowRecord(62L, null, null, newspaper, LocalDate.of(2026, 9, 1));
    when(records.findByStudentId(eq(1L), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
    var page =
        service.listByStudentId(1L, null, org.springframework.data.domain.PageRequest.of(0, 10));
    BorrowRecordResponse r = page.getContent().get(0);

    assertEquals(3L, r.itemId());
    assertEquals("Mapped Paper", r.itemTitle());
    assertEquals("NEWSPAPER", r.itemType());
  }

  @Test
  void responseShowsReturnedStatusWhenReturnDatePresent() {
    Book book = makeBook(1L, "Book", true);
    BorrowRecord record = makeBorrowRecord(63L, book, null, null, LocalDate.of(2026, 9, 1));
    record.setReturnDate(LocalDate.of(2026, 9, 5));
    when(records.findByStudentId(eq(1L), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
    var page =
        service.listByStudentId(1L, null, org.springframework.data.domain.PageRequest.of(0, 10));
    BorrowRecordResponse r = page.getContent().get(0);

    assertEquals("RETURNED", r.status());
    assertEquals(LocalDate.of(2026, 9, 5), r.returnDate());
  }

  @Test
  void responseComputesDaysOverdueForReturnedAfterDue() {
    Book book = makeBook(1L, "Book", true);
    BorrowRecord record = makeBorrowRecord(64L, book, null, null, LocalDate.of(2026, 7, 1));
    record.setReturnDate(LocalDate.of(2026, 7, 15));
    record.setDueDate(LocalDate.of(2026, 7, 10));
    when(records.findByStudentId(eq(1L), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
    var page =
        service.listByStudentId(1L, null, org.springframework.data.domain.PageRequest.of(0, 10));
    BorrowRecordResponse r = page.getContent().get(0);

    assertEquals(5L, r.daysOverdue(), "5 days between Jul 10 due and Jul 15 return");
  }

  // ==================== list() — status & search branches ====================

  @Test
  void listWithNullStatusReturnsAll() {
    Book book = makeBook(1L, "Book A", true);
    BorrowRecord r = makeBorrowRecord(70L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findAll(any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page = service.list(null, null, PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("Book A", page.getContent().get(0).itemTitle());
  }

  @Test
  void listWithBorrowedStatusFiltersUnreturned() {
    Book book = makeBook(1L, "Book B", true);
    BorrowRecord r = makeBorrowRecord(71L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findByReturnDateIsNull(any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page = service.list("BORROWED", null, PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("BORROWED", page.getContent().get(0).status());
  }

  @Test
  void listWithReturnedStatusFiltersReturned() {
    Book book = makeBook(1L, "Book C", true);
    BorrowRecord r = makeBorrowRecord(72L, book, null, null, LocalDate.of(2026, 9, 1));
    r.setReturnDate(LocalDate.of(2026, 9, 5));
    when(records.findByReturnDateIsNotNull(any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page = service.list("RETURNED", null, PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("RETURNED", page.getContent().get(0).status());
  }

  @Test
  void listWithSearchDelegatesToSearchMethod() {
    Book book = makeBook(1L, "Searchable Book", true);
    BorrowRecord r = makeBorrowRecord(73L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.search(eq("alice"), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page = service.list(null, "alice", PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("Searchable Book", page.getContent().get(0).itemTitle());
    verify(records).search(eq("alice"), any(Pageable.class));
  }

  // ==================== listByStudentId() — status branches ====================

  @Test
  void listByStudentIdBorrowedStatusFiltersUnreturned() {
    Book book = makeBook(1L, "Student Book", true);
    BorrowRecord r = makeBorrowRecord(80L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findByStudentIdAndReturnDateIsNull(eq(1L), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page =
        service.listByStudentId(1L, "BORROWED", PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("BORROWED", page.getContent().get(0).status());
  }

  @Test
  void listByStudentIdReturnedStatusFiltersReturned() {
    Book book = makeBook(1L, "Returned Book", true);
    BorrowRecord r = makeBorrowRecord(81L, book, null, null, LocalDate.of(2026, 9, 1));
    r.setReturnDate(LocalDate.of(2026, 9, 5));
    when(records.findByStudentIdAndReturnDateIsNotNull(eq(1L), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page =
        service.listByStudentId(1L, "RETURNED", PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("RETURNED", page.getContent().get(0).status());
  }

  @Test
  void listByStudentIdNullStatusReturnsAll() {
    Book book = makeBook(1L, "All Student Books", true);
    BorrowRecord r = makeBorrowRecord(82L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findByStudentId(eq(1L), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page = service.listByStudentId(1L, null, PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
  }

  // ==================== listByCurrentStudent() — success path ====================

  @Test
  void listByCurrentStudentReturnsRecordsForStudent() {
    when(students.findByAccountUsername("student1")).thenReturn(Optional.of(student));
    Book book = makeBook(1L, "My Book", true);
    BorrowRecord r = makeBorrowRecord(90L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findByStudentId(eq(1L), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page =
        service.listByCurrentStudent("student1", null, PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("My Book", page.getContent().get(0).itemTitle());
    assertEquals(90L, page.getContent().get(0).id());
  }

  @Test
  void listByCurrentStudentWithStatusPassesStatusThrough() {
    when(students.findByAccountUsername("student1")).thenReturn(Optional.of(student));
    Book book = makeBook(1L, "Active Book", true);
    BorrowRecord r = makeBorrowRecord(91L, book, null, null, LocalDate.of(2026, 9, 1));
    when(records.findByStudentIdAndReturnDateIsNull(eq(1L), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r)));

    Page<BorrowRecordResponse> page =
        service.listByCurrentStudent("student1", "BORROWED", PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals("BORROWED", page.getContent().get(0).status());
  }

  @Test
  void listByCurrentStudentStudentNotFound() {
    when(students.findByAccountUsername("ghost")).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> service.listByCurrentStudent("ghost", null, PageRequest.of(0, 10)));
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

  private Book makeBook(Long id, String title, boolean available) {
    Book b = new Book();
    setId(b, id);
    b.setTitle(title);
    b.setAvailable(available);
    return b;
  }

  private Magazine makeMagazine(Long id, String title, boolean available) {
    Magazine m = new Magazine();
    setId(m, id);
    m.setTitle(title);
    m.setAvailable(available);
    return m;
  }

  private Newspaper makeNewspaper(Long id, String title, boolean available) {
    Newspaper n = new Newspaper();
    setId(n, id);
    n.setTitle(title);
    n.setAvailable(available);
    return n;
  }

  private BorrowRecord makeBorrowRecord(
      Long id, Book book, Magazine magazine, Newspaper newspaper, LocalDate borrowDate) {
    BorrowRecord r = new BorrowRecord();
    setId(r, id);
    r.setBook(book);
    r.setMagazine(magazine);
    r.setNewspaper(newspaper);
    r.setStudent(student);
    r.setBorrowerName(student.getName());
    r.setBorrowerEmail(student.getEmail());
    r.setBorrowerPhone(student.getPhone());
    r.setBorrowDate(borrowDate);
    return r;
  }
}
