package com.example.lms.repository;

import com.example.lms.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
  org.springframework.data.domain.Page<Book>
      findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
          String title, String author, org.springframework.data.domain.Pageable pageable);

  boolean existsByIsbn(String isbn);

  boolean existsByIsbnAndIdNot(String isbn, Long id);

  long countByAvailable(boolean available);
}
