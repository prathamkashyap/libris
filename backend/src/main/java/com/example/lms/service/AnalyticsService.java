package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.repository.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
  private final BookRepository books;
  private final StudentProfileRepository students;
  private final LibrarianProfileRepository librarians;
  private final BorrowRecordRepository borrowRecords;

  public AnalyticsService(
      BookRepository books,
      StudentProfileRepository students,
      LibrarianProfileRepository librarians,
      BorrowRecordRepository borrowRecords) {
    this.books = books;
    this.students = students;
    this.librarians = librarians;
    this.borrowRecords = borrowRecords;
  }

  @Transactional(readOnly = true)
  public AnalyticsDashboardResponse dashboard() {
    var totalBooks = books.count();
    var totalStudents = students.count();
    var totalLibrarians = librarians.count();
    var borrowedBooks = books.countByAvailable(false);
    var availableBooks = books.countByAvailable(true);
    var overdueCount =
        borrowRecords
            .findByReturnDateIsNullAndBorrowDateBefore(LocalDate.now().minusDays(14))
            .size();
    return new AnalyticsDashboardResponse(
        totalBooks, totalStudents, totalLibrarians, borrowedBooks, availableBooks, overdueCount);
  }

  @Transactional(readOnly = true)
  public List<MonthlyTrend> trends() {
    return borrowRecords.findMonthlyTrends().stream()
        .map(
            row ->
                new MonthlyTrend(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).longValue()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TopBookResponse> topBooks(int limit) {
    return borrowRecords.findTopBooks(PageRequest.of(0, limit)).stream()
        .map(
            row ->
                new TopBookResponse(
                    (Long) row[0], (String) row[1], (String) row[2], ((Number) row[3]).longValue()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TopReaderResponse> topReaders(int limit) {
    return borrowRecords.findTopReaders(PageRequest.of(0, limit)).stream()
        .map(
            row ->
                new TopReaderResponse(
                    (Long) row[0], (String) row[1], (String) row[2], ((Number) row[3]).longValue()))
        .toList();
  }

  @Transactional(readOnly = true)
  public OverdueSummaryResponse overdue() {
    var cutoff = LocalDate.now().minusDays(14);
    var items =
        borrowRecords.findByReturnDateIsNullAndBorrowDateBefore(cutoff).stream()
            .map(
                r ->
                    new OverdueSummaryResponse.OverdueItem(
                        r.getId(),
                        itemTitle(r),
                        r.getBorrowerName(),
                        r.getBorrowDate(),
                        LocalDate.now().toEpochDay() - r.getBorrowDate().toEpochDay()))
            .toList();
    return new OverdueSummaryResponse(items.size(), items);
  }

  private String itemTitle(com.example.lms.entity.BorrowRecord r) {
    if (r.getBook() != null) return r.getBook().getTitle();
    if (r.getMagazine() != null) return r.getMagazine().getTitle();
    if (r.getNewspaper() != null) return r.getNewspaper().getTitle();
    return "Unknown";
  }
}
