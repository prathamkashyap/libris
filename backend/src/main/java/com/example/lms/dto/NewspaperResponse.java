package com.example.lms.dto;
import java.time.LocalDate;
public record NewspaperResponse(Long id,String title,String publisher,LocalDate publicationDate,String topHeadlines,boolean available){}
