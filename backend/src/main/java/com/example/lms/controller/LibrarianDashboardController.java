package com.example.lms.controller;

import com.example.lms.dto.DashboardResponse;
import com.example.lms.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/librarian/dashboard")
public class LibrarianDashboardController {

    private final DashboardService service;

    public LibrarianDashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public DashboardResponse getLibrarianDashboard() {
        return service.get();
    }
}