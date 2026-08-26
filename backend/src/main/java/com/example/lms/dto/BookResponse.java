package com.example.lms.dto;

import java.time.LocalDate;

public record BookResponse(
    Long id,
    String title,
    String author,
    String category,
    String isbn,
    LocalDate publishedDate,
    boolean available) {}
