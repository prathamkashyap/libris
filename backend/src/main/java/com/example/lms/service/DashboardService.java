package com.example.lms.service;

import com.example.lms.dto.DashboardResponse;
import com.example.lms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  private final StudentProfileRepository students;
  private final LibrarianProfileRepository librarians;
  private final BookRepository books;

  public DashboardService(
      StudentProfileRepository s, LibrarianProfileRepository l, BookRepository b) {
    students = s;
    librarians = l;
    books = b;
  }

  @Transactional(readOnly = true)
  public DashboardResponse get() {
    var available = books.countByAvailable(true);
    return new DashboardResponse(
        students.count(),
        librarians.count(),
        books.count(),
        books.countByAvailable(false),
        available);
  }
}
