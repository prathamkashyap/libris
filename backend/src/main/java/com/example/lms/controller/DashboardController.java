package com.example.lms.controller;

import com.example.lms.dto.DashboardResponse;
import com.example.lms.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Basic dashboard counts")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService s) {
    service = s;
  }

  @GetMapping
  @Operation(
      summary = "Get dashboard counts",
      description = "Total students, librarians, books, and availability counts.")
  public DashboardResponse get() {
    return service.get();
  }
}
