package com.example.lms.dto;
import java.time.LocalDate;
public record BorrowRecordSummary(
    Long id,
    String itemTitle,
    String itemType,
    LocalDate borrowDate,
    LocalDate returnDate,
    String status
) {}