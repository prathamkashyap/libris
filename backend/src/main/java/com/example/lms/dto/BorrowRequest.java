package com.example.lms.dto;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record BorrowRequest(@NotNull Long bookId,@NotNull Long studentId,@NotBlank @Size(max=100) String borrowerName,@NotBlank @Email(message="Invalid email address.") @Size(max=100) String borrowerEmail,@NotBlank @Size(max=20) String borrowerPhone,@NotNull LocalDate borrowDate){}
