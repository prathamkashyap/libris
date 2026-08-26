package com.example.lms.controller;

import com.example.lms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@Tag(
    name = "Reports",
    description = "CSV export endpoints for inventory, borrowing, and student data")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService s) {
    this.service = s;
  }

  @GetMapping("/inventory")
  @Operation(
      summary = "Inventory report",
      description = "Full catalogue inventory (books + magazines + newspapers) as CSV.")
  public ResponseEntity<Resource> inventory(@RequestParam(defaultValue = "csv") String format) {
    var csv = service.inventoryCsv();
    return csvResponse(csv, "inventory-report.csv");
  }

  @GetMapping("/borrowing")
  @Operation(
      summary = "Borrowing report",
      description = "All borrow records with optional date range filter, as CSV.")
  public ResponseEntity<Resource> borrowing(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "csv") String format) {
    var csv = service.borrowingCsv(from, to);
    return csvResponse(csv, "borrowing-report.csv");
  }

  @GetMapping("/overdue")
  @Operation(
      summary = "Overdue report",
      description = "All overdue items with days overdue, as CSV.")
  public ResponseEntity<Resource> overdue(@RequestParam(defaultValue = "csv") String format) {
    var csv = service.overdueCsv();
    return csvResponse(csv, "overdue-report.csv");
  }

  @GetMapping("/students")
  @Operation(
      summary = "Student report",
      description = "All students with total and active borrow counts, as CSV.")
  public ResponseEntity<Resource> students(@RequestParam(defaultValue = "csv") String format) {
    var csv = service.studentsCsv();
    return csvResponse(csv, "students-report.csv");
  }

  private ResponseEntity<Resource> csvResponse(String csv, String filename) {
    var bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var resource = new ByteArrayResource(bytes);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .contentLength(bytes.length)
        .body(resource);
  }
}
