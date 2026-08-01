package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.*;
import com.example.lms.repository.*;
import com.example.lms.util.CurrentUser;
import com.example.lms.util.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
  private final StudentProfileRepository students;
  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;

  public StudentService(
      StudentProfileRepository s,
      AccountRepository a,
      PasswordEncoder p,
      ApplicationEventPublisher events,
      CurrentUser currentUser) {
    students = s;
    accounts = a;
    passwords = p;
    this.events = events;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<StudentResponse> list(
      String search, org.springframework.data.domain.Pageable pageable) {
    var source =
        search == null || search.isBlank()
            ? students.findAll(pageable)
            : students.search(search, pageable);
    return source.map(this::response);
  }

  @Transactional(readOnly = true)
  public StudentResponse get(Long id) {
    return response(student(id));
  }

  @Transactional(readOnly = true)
  public StudentResponse getByUsername(String username) {
    var profile =
        students
            .findByAccountUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    return response(profile);
  }

  @Transactional
  public StudentResponse create(StudentRequest r) {
    if (accounts.existsByUsername(r.username()))
      throw new ConflictException("Username is already in use.");
    var a = new Account();
    a.setUsername(r.username().trim());
    a.setPasswordHash(passwords.encode(r.password()));
    a.setRole(Role.STUDENT);
    var p = new StudentProfile();
    p.setAccount(accounts.save(a));
    apply(p, r);
    var saved = response(students.save(p));
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.CREATE,
            AuditEntityType.STUDENT,
            saved.id(),
            "Student created: " + saved.name(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public StudentResponse update(Long id, StudentUpdateRequest r) {
    var p = student(id);
    if (!p.getAccount().getUsername().equals(r.username())
        && accounts.existsByUsername(r.username()))
      throw new ConflictException("Username is already in use.");
    p.getAccount().setUsername(r.username().trim());
    apply(p, r);
    var saved = response(students.save(p));
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.UPDATE,
            AuditEntityType.STUDENT,
            id,
            "Student updated: " + saved.name(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    var p = student(id);
    var name = p.getName();
    students.delete(p);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.DELETE,
            AuditEntityType.STUDENT,
            id,
            "Student deleted: " + name,
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
  }

  private StudentProfile student(Long id) {
    return students
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("Student %d was not found.".formatted(id)));
  }

  private void apply(StudentProfile p, StudentRequest r) {
    p.setName(r.name().trim());
    p.setEmail(StringUtils.blankToNull(r.email()));
    p.setPhone(StringUtils.blankToNull(r.phone()));
  }

  private void apply(StudentProfile p, StudentUpdateRequest r) {
    apply(p, new StudentRequest(r.username(), null, r.name(), r.email(), r.phone()));
  }

  private StudentResponse response(StudentProfile p) {
    var a = p.getAccount();
    return new StudentResponse(
        p.getId(),
        a.getId(),
        a.getUsername(),
        p.getName(),
        p.getEmail(),
        p.getPhone(),
        a.getRole().name());
  }
}
