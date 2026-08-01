package com.example.lms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "magazines")
public class Magazine extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 200)
  private String publisher;

  @Column(name = "issue_date")
  private LocalDate issueDate;

  @Column(length = 100)
  private String category;

  @Column(length = 200)
  private String featuredArticle;

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

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String v) {
    publisher = v;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public void setIssueDate(LocalDate v) {
    issueDate = v;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String v) {
    category = v;
  }

  public String getFeaturedArticle() {
    return featuredArticle;
  }

  public void setFeaturedArticle(String v) {
    featuredArticle = v;
  }

  public boolean isAvailable() {
    return available;
  }

  public void setAvailable(boolean v) {
    available = v;
  }
}
