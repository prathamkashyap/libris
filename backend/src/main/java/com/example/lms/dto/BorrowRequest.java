package com.example.lms.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record BorrowRequest(
    Long bookId,
    Long magazineId,
    Long newspaperId,
    @NotNull Long studentId,
    String borrowerName,
    String borrowerEmail,
    String borrowerPhone,
    @NotNull LocalDate borrowDate) {}
