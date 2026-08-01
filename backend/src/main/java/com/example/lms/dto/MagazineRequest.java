package com.example.lms.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record MagazineRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 200) String publisher,
    LocalDate issueDate,
    @Size(max = 100) String category,
    @Size(max = 200) String featuredArticle) {}
