package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lms.entity.Book;
import com.example.lms.repository.BookRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class BookRepositoryTest {
  @Autowired BookRepository books;

  @Test
  void persistsAuditTimestampsAndRejectsDuplicateIsbn() {
    Book saved = books.saveAndFlush(book("Repository Test", "9780000000001"));
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThatThrownBy(() -> books.saveAndFlush(book("Duplicate", "9780000000001")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Book book(String title, String isbn) {
    Book book = new Book();
    book.setTitle(title);
    book.setIsbn(isbn);
    book.setPublishedDate(LocalDate.of(2020, 1, 1));
    return book;
  }
}
