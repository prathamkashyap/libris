package com.example.lms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "librarian_profiles")
public class LibrarianProfile extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, unique = true)
  private Account account;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private int age;

  @Column(nullable = false, length = 20)
  private String phone;

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public void setAccount(Account v) {
    account = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int v) {
    age = v;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String v) {
    phone = v;
  }
}
