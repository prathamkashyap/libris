package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.service.BorrowRecordService;
import com.example.lms.service.StudentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentDashboardController {

  private final StudentService studentService;
  private final BorrowRecordService borrowService;

  public StudentDashboardController(StudentService s, BorrowRecordService b) {
    this.studentService = s;
    this.borrowService = b;
  }

  @GetMapping("/dashboard")
  public StudentDashboardResponse getDashboard(Authentication authentication) {
    var student = studentService.getByUsername(authentication.getName());

    var borrowed =
        borrowService.listByStudentId(student.id(), "BORROWED", org.springframework.data.domain.Pageable.unpaged());
    var returned =
        borrowService.listByStudentId(student.id(), "RETURNED", org.springframework.data.domain.Pageable.unpaged());

    var currentBorrows =
        borrowed.getContent().stream()
            .map(this::toSummary)
            .toList();

    var history =
        returned.getContent().stream()
            .map(this::toSummary)
            .toList();

    return new StudentDashboardResponse(
        student.id(),
        student.name(),
        student.email(),
        student.phone(),
        student.username(),
        currentBorrows,
        history);
  }

  private BorrowRecordSummary toSummary(com.example.lms.dto.BorrowRecordResponse r) {
    return new BorrowRecordSummary(
        r.id(), r.itemTitle(), r.itemType(), r.borrowDate(), r.returnDate(), r.status());
  }
}
