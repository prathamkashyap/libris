package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.*;
import com.example.lms.repository.*;
import com.example.lms.util.CurrentUser;
import com.example.lms.util.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MagazineService {
  private final MagazineRepository magazines;
  private final BorrowRecordRepository records;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;

  public MagazineService(
      MagazineRepository magazines,
      BorrowRecordRepository records,
      ApplicationEventPublisher events,
      CurrentUser currentUser) {
    this.magazines = magazines;
    this.records = records;
    this.events = events;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public Page<MagazineResponse> list(String search, Pageable pageable) {
    var source =
        search == null || search.isBlank()
            ? magazines.findAll(pageable)
            : magazines.searchMagazines(search, pageable);
    return source.map(this::response);
  }

  @Transactional(readOnly = true)
  public MagazineResponse get(Long id) {
    return response(magazine(id));
  }

  @Transactional
  public MagazineResponse create(MagazineRequest request) {
    var entity = new Magazine();
    apply(entity, request);
    entity.setAvailable(true);
    var saved = save(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.CREATE,
            AuditEntityType.MAGAZINE,
            saved.id(),
            "Magazine created: " + saved.title(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public MagazineResponse update(Long id, MagazineRequest request) {
    var entity = magazine(id);
    apply(entity, request);
    var saved = save(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.UPDATE,
            AuditEntityType.MAGAZINE,
            id,
            "Magazine updated: " + saved.title(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    var entity = magazine(id);
    var title = entity.getTitle();
    if (records.existsByMagazineId(id))
      throw new ConflictException("A magazine with borrow history cannot be deleted.");
    magazines.delete(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.DELETE,
            AuditEntityType.MAGAZINE,
            id,
            "Magazine deleted: " + title,
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
  }

  private Magazine magazine(Long id) {
    return magazines
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Magazine not found."));
  }

  private MagazineResponse save(Magazine magazine) {
    return response(magazines.saveAndFlush(magazine));
  }

  private void apply(Magazine entity, MagazineRequest r) {
    entity.setTitle(r.title().trim());
    entity.setPublisher(StringUtils.blankToNull(r.publisher()));
    entity.setCategory(StringUtils.blankToNull(r.category()));
    entity.setFeaturedArticle(StringUtils.blankToNull(r.featuredArticle()));
    entity.setIssueDate(r.issueDate());
  }

  private MagazineResponse response(Magazine m) {
    return new MagazineResponse(
        m.getId(),
        m.getTitle(),
        m.getPublisher(),
        m.getIssueDate(),
        m.getCategory(),
        m.getFeaturedArticle(),
        m.isAvailable());
  }
}
