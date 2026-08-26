package com.example.lms.dto;

import java.util.List;

public record StudentDashboardResponse(
    Long studentId,
    String name,
    String email,
    String phone,
    String username,
    List<BorrowRecordSummary> currentBorrows,
    List<BorrowRecordSummary> borrowHistory) {}
