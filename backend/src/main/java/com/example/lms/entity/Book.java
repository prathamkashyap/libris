package com.example.lms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "books")
public class Book extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 200)
  private String author;

  @Column(length = 50, unique = true)
  private String isbn;

  @Column(name = "published_date")
  private LocalDate publishedDate;

  @Column(nullable = false)
  private boolean available = true;

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String v) {
    author = v;
  }

  public String getIsbn() {
    return isbn;
  }

  public void setIsbn(String v) {
    isbn = v;
  }

  public LocalDate getPublishedDate() {
    return publishedDate;
  }

  public void setPublishedDate(LocalDate v) {
    publishedDate = v;
  }

  public boolean isAvailable() {
    return available;
  }

  public void setAvailable(boolean v) {
    available = v;
  }
}
