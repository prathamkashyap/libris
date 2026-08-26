package com.example.lms.dto;

import java.time.LocalDate;

public record MagazineResponse(
    Long id,
    String title,
    String publisher,
    LocalDate issueDate,
    String category,
    String featuredArticle,
    boolean available) {}
