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
public class NewspaperService {
  private final NewspaperRepository newspapers;
  private final BorrowRecordRepository records;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;

  public NewspaperService(
      NewspaperRepository newspapers,
      BorrowRecordRepository records,
      ApplicationEventPublisher events,
      CurrentUser currentUser) {
    this.newspapers = newspapers;
    this.records = records;
    this.events = events;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public Page<NewspaperResponse> list(String search, Pageable pageable) {
    var source =
        search == null || search.isBlank()
            ? newspapers.findAll(pageable)
            : newspapers.searchNewspapers(search, pageable);
    return source.map(this::response);
  }

  @Transactional(readOnly = true)
  public NewspaperResponse get(Long id) {
    return response(newspaper(id));
  }

  @Transactional
  public NewspaperResponse create(NewspaperRequest request) {
    var entity = new Newspaper();
    apply(entity, request);
    entity.setAvailable(true);
    var saved = save(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.CREATE,
            AuditEntityType.NEWSPAPER,
            saved.id(),
            "Newspaper created: " + saved.title(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public NewspaperResponse update(Long id, NewspaperRequest request) {
    var entity = newspaper(id);
    apply(entity, request);
    var saved = save(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.UPDATE,
            AuditEntityType.NEWSPAPER,
            id,
            "Newspaper updated: " + saved.title(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    var entity = newspaper(id);
    var title = entity.getTitle();
    if (records.existsByNewspaperId(id))
      throw new ConflictException("A newspaper with borrow history cannot be deleted.");
    newspapers.delete(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.DELETE,
            AuditEntityType.NEWSPAPER,
            id,
            "Newspaper deleted: " + title,
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
  }

  private Newspaper newspaper(Long id) {
    return newspapers
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Newspaper not found."));
  }

  private NewspaperResponse save(Newspaper newspaper) {
    return response(newspapers.saveAndFlush(newspaper));
  }

  private void apply(Newspaper entity, NewspaperRequest r) {
    entity.setTitle(r.title().trim());
    entity.setPublisher(StringUtils.blankToNull(r.publisher()));
    entity.setTopHeadlines(StringUtils.blankToNull(r.topHeadlines()));
    entity.setPublicationDate(r.publicationDate());
  }

  private NewspaperResponse response(Newspaper n) {
    return new NewspaperResponse(
        n.getId(),
        n.getTitle(),
        n.getPublisher(),
        n.getPublicationDate(),
        n.getTopHeadlines(),
        n.isAvailable());
  }
}
