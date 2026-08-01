package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Dashboard aggregates, trends, and top lists")
public class AnalyticsController {
  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService s) {
    this.service = s;
  }

  @GetMapping("/dashboard")
  @Operation(
      summary = "Dashboard stats",
      description = "Aggregate counts for books, students, and overdue items.")
  public AnalyticsDashboardResponse dashboard() {
    return service.dashboard();
  }

  @GetMapping("/trends")
  @Operation(
      summary = "Monthly borrowing trends",
      description = "Year/month borrow counts for the entire record history.")
  public List<MonthlyTrend> trends() {
    return service.trends();
  }

  @GetMapping("/top-books")
  @Operation(summary = "Most borrowed books", description = "Top N books by borrow count.")
  public List<TopBookResponse> topBooks(@RequestParam(defaultValue = "5") int limit) {
    return service.topBooks(limit);
  }

  @GetMapping("/top-readers")
  @Operation(summary = "Most active readers", description = "Top N students by borrow count.")
  public List<TopReaderResponse> topReaders(@RequestParam(defaultValue = "5") int limit) {
    return service.topReaders(limit);
  }

  @GetMapping("/overdue")
  @Operation(
      summary = "Overdue summary",
      description = "All currently overdue items with days overdue.")
  public OverdueSummaryResponse overdue() {
    return service.overdue();
  }
}
