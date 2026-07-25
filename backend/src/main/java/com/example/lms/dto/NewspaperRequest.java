package com.example.lms.dto;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record NewspaperRequest(@NotBlank @Size(max=200) String title,@Size(max=200) String publisher,LocalDate publicationDate,@Size(max=500) String topHeadlines){}
