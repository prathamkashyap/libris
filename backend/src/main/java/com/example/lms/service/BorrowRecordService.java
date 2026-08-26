package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.*;
import com.example.lms.repository.*;
import com.example.lms.util.CurrentUser;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowRecordService {
  private final BorrowRecordRepository records;
  private final BookRepository books;
  private final MagazineRepository magazines;
  private final NewspaperRepository newspapers;
  private final StudentProfileRepository students;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;

  public BorrowRecordService(
      BorrowRecordRepository r,
      BookRepository b,
      MagazineRepository m,
      NewspaperRepository n,
      StudentProfileRepository s,
      ApplicationEventPublisher events,
      CurrentUser currentUser) {
    records = r;
    books = b;
    magazines = m;
    newspapers = n;
    students = s;
    this.events = events;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public Page<BorrowRecordResponse> list(String status, String search, Pageable pageable) {
    if (search != null && !search.isBlank()) {
      return records.search(search, pageable).map(this::response);
    }
    Page<BorrowRecord> source =
        "BORROWED".equalsIgnoreCase(status)
            ? records.findByReturnDateIsNull(pageable)
            : "RETURNED".equalsIgnoreCase(status)
                ? records.findByReturnDateIsNotNull(pageable)
                : records.findAll(pageable);
    return source.map(this::response);
  }

  @Transactional(readOnly = true)
  public Page<BorrowRecordResponse> listByStudentId(
      Long studentId, String status, Pageable pageable) {
    Page<BorrowRecord> source =
        "BORROWED".equalsIgnoreCase(status)
            ? records.findByStudentIdAndReturnDateIsNull(studentId, pageable)
            : "RETURNED".equalsIgnoreCase(status)
                ? records.findByStudentIdAndReturnDateIsNotNull(studentId, pageable)
                : records.findByStudentId(studentId, pageable);
    return source.map(this::response);
  }

  @Transactional
  public BorrowRecordResponse borrow(BorrowRequest r) {
    long selectedItems =
        java.util.stream.Stream.of(r.bookId(), r.magazineId(), r.newspaperId())
            .filter(java.util.Objects::nonNull)
            .count();
    if (selectedItems != 1) {
      throw new BusinessRuleException("INVALID_REQUEST", "Select exactly one item to borrow.");
    }
    var student =
        students
            .findById(r.studentId())
            .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    var record = new BorrowRecord();
    record.setStudent(student);
    record.setBorrowerName(student.getName());
    record.setBorrowerEmail(student.getEmail());
    record.setBorrowerPhone(student.getPhone());
    record.setBorrowDate(r.borrowDate());
    record.setDueDate(r.dueDate() != null ? r.dueDate() : r.borrowDate().plusDays(14));

    String itemTitle = null;
    if (r.bookId() != null) {
      var book =
          books
              .findById(r.bookId())
              .orElseThrow(() -> new ResourceNotFoundException("Book not found."));
      if (!book.isAvailable())
        throw new BusinessRuleException("UNAVAILABLE", "Book is not available.");
      book.setAvailable(false);
      record.setBook(book);
      itemTitle = book.getTitle();
    } else if (r.magazineId() != null) {
      var magazine =
          magazines
              .findById(r.magazineId())
              .orElseThrow(() -> new ResourceNotFoundException("Magazine not found."));
      if (!magazine.isAvailable())
        throw new BusinessRuleException("UNAVAILABLE", "Magazine is not available.");
      magazine.setAvailable(false);
      record.setMagazine(magazine);
      itemTitle = magazine.getTitle();
    } else if (r.newspaperId() != null) {
      var newspaper =
          newspapers
              .findById(r.newspaperId())
              .orElseThrow(() -> new ResourceNotFoundException("Newspaper not found."));
      if (!newspaper.isAvailable())
        throw new BusinessRuleException("UNAVAILABLE", "Newspaper is not available.");
      newspaper.setAvailable(false);
      record.setNewspaper(newspaper);
      itemTitle = newspaper.getTitle();
    } else {
      throw new BusinessRuleException("INVALID_REQUEST", "Must specify an item to borrow.");
    }

    var saved = response(records.save(record));
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.BORROW,
            AuditEntityType.BORROW_RECORD,
            saved.id(),
            "Borrowed: " + itemTitle + " by " + student.getName(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public void returnBook(Long id) {
    var record =
        records
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Borrow record not found."));
    if (record.getReturnDate() != null)
      throw new BusinessRuleException("ALREADY_RETURNED", "Already returned.");

    record.setReturnDate(LocalDate.now());
    String itemTitle = null;
    if (record.getBook() != null) {
      record.getBook().setAvailable(true);
      itemTitle = record.getBook().getTitle();
    }
    if (record.getMagazine() != null) {
      record.getMagazine().setAvailable(true);
      itemTitle = record.getMagazine().getTitle();
    }
    if (record.getNewspaper() != null) {
      record.getNewspaper().setAvailable(true);
      itemTitle = record.getNewspaper().getTitle();
    }

    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.RETURN,
            AuditEntityType.BORROW_RECORD,
            id,
            "Returned: " + (itemTitle != null ? itemTitle : "item"),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
  }

  private BorrowRecordResponse response(BorrowRecord r) {
    Long itemId = null;
    String itemTitle = null;
    String itemType = null;
    if (r.getBook() != null) {
      itemId = r.getBook().getId();
      itemTitle = r.getBook().getTitle();
      itemType = "BOOK";
    } else if (r.getMagazine() != null) {
      itemId = r.getMagazine().getId();
      itemTitle = r.getMagazine().getTitle();
      itemType = "MAGAZINE";
    } else if (r.getNewspaper() != null) {
      itemId = r.getNewspaper().getId();
      itemTitle = r.getNewspaper().getTitle();
      itemType = "NEWSPAPER";
    }

    LocalDate dueDate = r.getDueDate() != null ? r.getDueDate() : r.getBorrowDate().plusDays(14);
    long daysOverdue = 0L;
    if (r.getReturnDate() == null) {
      if (LocalDate.now().isAfter(dueDate)) {
        daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
      }
    } else {
      if (r.getReturnDate().isAfter(dueDate)) {
        daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(dueDate, r.getReturnDate());
      }
    }

    return new BorrowRecordResponse(
        r.getId(),
        itemId,
        itemTitle,
        itemType,
        r.getStudent().getId(),
        r.getBorrowerName(),
        r.getBorrowerEmail(),
        r.getBorrowerPhone(),
        r.getBorrowDate(),
        dueDate,
        r.getReturnDate(),
        r.getReturnDate() == null ? "BORROWED" : "RETURNED",
        daysOverdue);
  }
}
