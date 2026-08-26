package com.example.lms.dto;

import java.time.LocalDate;

public record BorrowRecordResponse(
    Long id,
    Long itemId,
    String itemTitle,
    String itemType,
    Long studentId,
    String borrowerName,
    String borrowerEmail,
    String borrowerPhone,
    LocalDate borrowDate,
    LocalDate dueDate,
    LocalDate returnDate,
    String status,
    Long daysOverdue) {}
