package com.example.lms.dto;
import java.time.LocalDate;
public record BorrowRecordResponse(Long id,Long bookId,String bookTitle,Long studentId,String borrowerName,String borrowerEmail,String borrowerPhone,LocalDate borrowDate,LocalDate returnDate,String status){}
