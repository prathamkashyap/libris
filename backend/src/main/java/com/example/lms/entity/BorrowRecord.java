package com.example.lms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "borrow_records")
public class BorrowRecord extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id")
  private Book book;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "magazine_id")
  private Magazine magazine;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "newspaper_id")
  private Newspaper newspaper;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id")
  private StudentProfile student;

  @Column(name = "borrower_name", nullable = false, length = 100)
  private String borrowerName;

  @Column(name = "borrower_email", nullable = false, length = 100)
  private String borrowerEmail;

  @Column(name = "borrower_phone", nullable = false, length = 20)
  private String borrowerPhone;

  @Column(name = "borrow_date", nullable = false)
  private LocalDate borrowDate;

  @Column(name = "return_date")
  private LocalDate returnDate;

  public Long getId() {
    return id;
  }

  public Book getBook() {
    return book;
  }

  public void setBook(Book v) {
    book = v;
  }

  public Magazine getMagazine() {
    return magazine;
  }

  public void setMagazine(Magazine v) {
    magazine = v;
  }

  public Newspaper getNewspaper() {
    return newspaper;
  }

  public void setNewspaper(Newspaper v) {
    newspaper = v;
  }

  public StudentProfile getStudent() {
    return student;
  }

  public void setStudent(StudentProfile v) {
    student = v;
  }

  public String getBorrowerName() {
    return borrowerName;
  }

  public void setBorrowerName(String v) {
    borrowerName = v;
  }

  public String getBorrowerEmail() {
    return borrowerEmail;
  }

  public void setBorrowerEmail(String v) {
    borrowerEmail = v;
  }

  public String getBorrowerPhone() {
    return borrowerPhone;
  }

  public void setBorrowerPhone(String v) {
    borrowerPhone = v;
  }

  public LocalDate getBorrowDate() {
    return borrowDate;
  }

  public void setBorrowDate(LocalDate v) {
    borrowDate = v;
  }

  public LocalDate getReturnDate() {
    return returnDate;
  }

  public void setReturnDate(LocalDate v) {
    returnDate = v;
  }
}
