package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.repository.StudentProfileRepository;
import com.example.lms.service.BorrowRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/borrow-records")
@Tag(name = "Borrow Records", description = "Borrow and return workflow")
public class BorrowRecordController {
  private final BorrowRecordService service;
  private final StudentProfileRepository studentRepo;

  public BorrowRecordController(BorrowRecordService s, StudentProfileRepository sr) {
    service = s;
    studentRepo = sr;
  }

  @GetMapping
  @Operation(
      summary = "List borrow records",
      description = "Paginated list filtered by status and/or search by borrower name or email.")
  public org.springframework.data.domain.Page<BorrowRecordResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(status, query, org.springframework.data.domain.PageRequest.of(page, size));
  }

  @GetMapping("/my")
  @Operation(summary = "My borrow records", description = "Current user's borrow history.")
  public org.springframework.data.domain.Page<BorrowRecordResponse> myRecords(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      Authentication auth) {
    Long studentId =
        studentRepo
            .findByAccountUsername(auth.getName())
            .orElseThrow(
                () -> new com.example.lms.exception.ResourceNotFoundException("Student not found."))
            .getId();
    return service.listByStudentId(
        studentId, status, org.springframework.data.domain.PageRequest.of(page, size));
  }

  @PostMapping
  @Operation(summary = "Borrow an item")
  @ApiResponse(responseCode = "201", description = "Item borrowed")
  public ResponseEntity<BorrowRecordResponse> borrow(@Valid @RequestBody BorrowRequest r) {
    var created = service.borrow(r);
    return ResponseEntity.created(URI.create("/api/borrow-records/" + created.id())).body(created);
  }

  @PostMapping("/{id}/return")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Return an item")
  @ApiResponse(responseCode = "204", description = "Item returned")
  public void returnBook(@PathVariable Long id) {
    service.returnBook(id);
  }
}
