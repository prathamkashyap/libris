package com.example.lms.service;

import com.example.lms.repository.*;
import com.example.lms.util.OverdueCalculator;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  private static final int BATCH_SIZE = 500;

  private final BookRepository books;
  private final MagazineRepository magazines;
  private final NewspaperRepository newspapers;
  private final BorrowRecordRepository borrowRecords;
  private final StudentProfileRepository students;

  public ReportService(
      BookRepository books,
      MagazineRepository magazines,
      NewspaperRepository newspapers,
      BorrowRecordRepository borrowRecords,
      StudentProfileRepository students) {
    this.books = books;
    this.magazines = magazines;
    this.newspapers = newspapers;
    this.borrowRecords = borrowRecords;
    this.students = students;
  }

  @Transactional(readOnly = true)
  public String inventoryCsv() {
    var header =
        csvLine("Type", "ID", "Title", "Author/Publisher", "Category", "ISBN", "Date", "Available");
    var rows = new ArrayList<String>();
    long lastBookId = 0;
    while (true) {
      var batch =
          books.findByIdGreaterThan(lastBookId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
      if (batch.isEmpty()) break;
      batch.forEach(
          b ->
              rows.add(
                  csvLine(
                      "Book",
                      b.getId(),
                      b.getTitle(),
                      b.getAuthor(),
                      b.getCategory() != null ? b.getCategory() : "",
                      b.getIsbn(),
                      b.getPublishedDate() != null ? b.getPublishedDate().toString() : "",
                      b.isAvailable() ? "Yes" : "No")));
      lastBookId = batch.get(batch.size() - 1).getId();
    }
    long lastMagId = 0;
    while (true) {
      var batch =
          magazines.findByIdGreaterThan(lastMagId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
      if (batch.isEmpty()) break;
      batch.forEach(
          m ->
              rows.add(
                  csvLine(
                      "Magazine",
                      m.getId(),
                      m.getTitle(),
                      m.getPublisher(),
                      m.getCategory() != null ? m.getCategory() : "",
                      "",
                      m.getIssueDate() != null ? m.getIssueDate().toString() : "",
                      m.isAvailable() ? "Yes" : "No")));
      lastMagId = batch.get(batch.size() - 1).getId();
    }
    long lastNpId = 0;
    while (true) {
      var batch =
          newspapers.findByIdGreaterThan(lastNpId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
      if (batch.isEmpty()) break;
      batch.forEach(
          n ->
              rows.add(
                  csvLine(
                      "Newspaper",
                      n.getId(),
                      n.getTitle(),
                      n.getPublisher(),
                      "",
                      "",
                      n.getPublicationDate() != null ? n.getPublicationDate().toString() : "",
                      n.isAvailable() ? "Yes" : "No")));
      lastNpId = batch.get(batch.size() - 1).getId();
    }
    return Stream.concat(Stream.of(header), rows.stream()).collect(Collectors.joining("\n"));
  }

  @Transactional(readOnly = true)
  public String borrowingCsv(java.time.LocalDate from, java.time.LocalDate to) {
    var header =
        csvLine(
            "ID",
            "Item Title",
            "Item Type",
            "Borrower Name",
            "Borrower Email",
            "Borrower Phone",
            "Borrow Date",
            "Due Date",
            "Return Date",
            "Status");
    var rows = new ArrayList<String>();
    if (from != null && to != null) {
      long lastId = 0;
      while (true) {
        var batch =
            borrowRecords.findByBorrowDateBetweenAndIdGreaterThan(
                from, to, lastId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
        if (batch.isEmpty()) break;
        batch.forEach(r -> rows.add(borrowRow(r)));
        lastId = batch.get(batch.size() - 1).getId();
      }
    } else {
      long lastId = 0;
      while (true) {
        var batch =
            borrowRecords.findByIdGreaterThan(lastId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
        if (batch.isEmpty()) break;
        batch.forEach(r -> rows.add(borrowRow(r)));
        lastId = batch.get(batch.size() - 1).getId();
      }
    }
    return Stream.concat(Stream.of(header), rows.stream()).collect(Collectors.joining("\n"));
  }

  @Transactional(readOnly = true)
  public String studentsCsv() {
    var borrowCounts = new java.util.HashMap<Long, long[]>();
    long lastId = 0;
    while (true) {
      var batch =
          borrowRecords.findByIdGreaterThan(lastId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
      if (batch.isEmpty()) break;
      for (var r : batch) {
        var studentId = r.getStudent() != null ? r.getStudent().getId() : -1L;
        var counts = borrowCounts.computeIfAbsent(studentId, k -> new long[] {0, 0});
        counts[0]++;
        if (r.getReturnDate() == null) counts[1]++;
      }
      lastId = batch.get(batch.size() - 1).getId();
    }
    var header =
        csvLine("ID", "Name", "Email", "Phone", "Username", "Total Borrows", "Active Borrows");
    var rows = new ArrayList<String>();
    long lastStudentId = 0;
    while (true) {
      var batch =
          students.findByIdGreaterThanWithAccount(
              lastStudentId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
      if (batch.isEmpty()) break;
      batch.forEach(
          s -> {
            var counts = borrowCounts.getOrDefault(s.getId(), new long[] {0, 0});
            rows.add(
                csvLine(
                    s.getId(),
                    s.getName(),
                    s.getEmail(),
                    s.getPhone(),
                    s.getAccount().getUsername(),
                    counts[0],
                    counts[1]));
          });
      lastStudentId = batch.get(batch.size() - 1).getId();
    }
    return Stream.concat(Stream.of(header), rows.stream()).collect(Collectors.joining("\n"));
  }

  @Transactional(readOnly = true)
  public String overdueCsv() {
    var header = csvLine("ID", "Item Title", "Borrower Name", "Borrow Date", "Days Overdue");
    var rows = new ArrayList<String>();
    long lastId = 0;
    while (true) {
      var batch =
          borrowRecords.findByIdGreaterThan(lastId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
      if (batch.isEmpty()) break;
      for (var r : batch) {
        if (r.getReturnDate() == null && OverdueCalculator.isOverdue(r)) {
          rows.add(
              csvLine(
                  r.getId(),
                  itemTitle(r),
                  r.getBorrowerName(),
                  r.getBorrowDate().toString(),
                  OverdueCalculator.daysOverdue(r)));
        }
      }
      lastId = batch.get(batch.size() - 1).getId();
    }
    return Stream.concat(Stream.of(header), rows.stream()).collect(Collectors.joining("\n"));
  }

  private String borrowRow(com.example.lms.entity.BorrowRecord r) {
    var itemType =
        r.getBook() != null ? "BOOK" : r.getMagazine() != null ? "MAGAZINE" : "NEWSPAPER";
    var itemTitle =
        r.getBook() != null
            ? r.getBook().getTitle()
            : r.getMagazine() != null
                ? r.getMagazine().getTitle()
                : r.getNewspaper() != null ? r.getNewspaper().getTitle() : "";
    var dueDate = r.getDueDate() != null ? r.getDueDate() : r.getBorrowDate().plusDays(14);
    return csvLine(
        r.getId(),
        itemTitle,
        itemType,
        r.getBorrowerName(),
        r.getBorrowerEmail(),
        r.getBorrowerPhone(),
        r.getBorrowDate().toString(),
        dueDate.toString(),
        r.getReturnDate() != null ? r.getReturnDate().toString() : "",
        r.getReturnDate() == null ? "BORROWED" : "RETURNED");
  }

  private String itemTitle(com.example.lms.entity.BorrowRecord r) {
    if (r.getBook() != null) return r.getBook().getTitle();
    if (r.getMagazine() != null) return r.getMagazine().getTitle();
    if (r.getNewspaper() != null) return r.getNewspaper().getTitle();
    return "Unknown";
  }

  private String csvLine(Object... values) {
    return Stream.of(values)
        .map(
            v -> {
              var s = v != null ? v.toString() : "";
              return s.contains(",") || s.contains("\"") || s.contains("\n")
                  ? "\"" + s.replace("\"", "\"\"") + "\""
                  : s;
            })
        .collect(Collectors.joining(","));
  }
}
