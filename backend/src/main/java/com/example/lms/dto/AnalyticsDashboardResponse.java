package com.example.lms.dto;
public record AnalyticsDashboardResponse(
    long totalBooks, long totalStudents, long totalLibrarians,
    long borrowedBooks, long availableBooks, long overdueCount
){}
