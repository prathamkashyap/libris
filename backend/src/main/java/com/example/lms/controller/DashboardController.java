package com.example.lms.controller;
import com.example.lms.dto.DashboardResponse; import com.example.lms.service.DashboardService; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/dashboard") public class DashboardController {private final DashboardService service;public DashboardController(DashboardService s){service=s;}@GetMapping public DashboardResponse get(){return service.get();}}
