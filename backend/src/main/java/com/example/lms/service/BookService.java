package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.*;
import com.example.lms.repository.*;
import com.example.lms.util.CurrentUser;
import com.example.lms.util.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {
  private final BookRepository books;
  private final BorrowRecordRepository records;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;

  public BookService(
      BookRepository books,
      BorrowRecordRepository records,
      ApplicationEventPublisher events,
      CurrentUser currentUser) {
    this.books = books;
    this.records = records;
    this.events = events;
    this.currentUser = currentUser;
  }

  @Cacheable(
      cacheNames = "books",
      key = "'list:' + (#search ?: '') + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<BookResponse> list(
      String search, org.springframework.data.domain.Pageable pageable) {
    var source =
        search == null || search.isBlank()
            ? books.findAll(pageable)
            : books.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
                search, search, pageable);
    return source.map(this::response);
  }

  @Cacheable(cacheNames = "books", key = "'get:' + #id")
  @Transactional(readOnly = true)
  public BookResponse get(Long id) {
    return response(book(id));
  }

  @CacheEvict(cacheNames = "books", allEntries = true)
  @Transactional
  public BookResponse create(BookRequest request) {
    var entity = new Book();
    apply(entity, request);
    validateUniqueIsbn(entity.getIsbn(), null);
    entity.setAvailable(true);
    var saved = save(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.CREATE,
            AuditEntityType.BOOK,
            saved.id(),
            "Book created: " + saved.title(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @CacheEvict(cacheNames = "books", allEntries = true)
  @Transactional
  public BookResponse update(Long id, BookRequest request) {
    var entity = book(id);
    apply(entity, request);
    validateUniqueIsbn(entity.getIsbn(), id);
    var saved = save(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.UPDATE,
            AuditEntityType.BOOK,
            id,
            "Book updated: " + saved.title(),
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
    return saved;
  }

  @CacheEvict(cacheNames = "books", allEntries = true)
  @Transactional
  public void delete(Long id) {
    var entity = book(id);
    var title = entity.getTitle();
    if (records.existsByBookId(id))
      throw new ConflictException("A book with borrow history cannot be deleted.");
    books.delete(entity);
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.DELETE,
            AuditEntityType.BOOK,
            id,
            "Book deleted: " + title,
            actor.id(),
            actor.username(),
            actor.role(),
            actor.ipAddress(),
            actor.userAgent()));
  }

  private Book book(Long id) {
    return books
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Book %d was not found.".formatted(id)));
  }

  private void validateUniqueIsbn(String isbn, Long currentBookId) {
    if (isbn != null
        && (currentBookId == null
            ? books.existsByIsbn(isbn)
            : books.existsByIsbnAndIdNot(isbn, currentBookId)))
      throw new ConflictException("ISBN already exists.");
  }

  private BookResponse save(Book book) {
    try {
      return response(books.saveAndFlush(book));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("ISBN already exists.");
    }
  }

  private void apply(Book entity, BookRequest r) {
    entity.setTitle(r.title().trim());
    entity.setAuthor(StringUtils.blankToNull(r.author()));
    entity.setCategory(StringUtils.blankToNull(r.category()));
    entity.setIsbn(StringUtils.blankToNull(r.isbn()));
    entity.setPublishedDate(r.publishedDate());
  }

  private BookResponse response(Book b) {
    return new BookResponse(
        b.getId(),
        b.getTitle(),
        b.getAuthor(),
        b.getCategory(),
        b.getIsbn(),
        b.getPublishedDate(),
        b.isAvailable());
  }
}
