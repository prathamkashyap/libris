package com.example.lms.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import java.time.LocalDate;
public record BookRequest(@NotBlank @Size(max=200) String title, @Size(max=200) String author, @Size(max=50) String isbn, LocalDate publishedDate) { }
