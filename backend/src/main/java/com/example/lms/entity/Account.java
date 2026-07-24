package com.example.lms.entity;
import jakarta.persistence.*;
@Entity @Table(name="accounts")
public class Account extends AuditableEntity { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true,length=50) private String username; @Column(name="password_hash",nullable=false,length=100) private String passwordHash; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role; @Column(nullable=false) private boolean enabled=true;
 public Long getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public Role getRole(){return role;} public void setRole(Role v){role=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} }
