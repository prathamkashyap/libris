package com.example.lms.repository;

import com.example.lms.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {
  org.springframework.data.domain.Page<Book>
      findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
          String title, String author, org.springframework.data.domain.Pageable pageable);

  boolean existsByIsbn(String isbn);

  boolean existsByIsbnAndIdNot(String isbn, Long id);

  long countByAvailable(boolean available);

  java.util.List<Book> findByIdGreaterThan(
      Long id, org.springframework.data.domain.Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Book b WHERE b.id = :id")
  java.util.Optional<Book> findByIdForBorrow(Long id);
}
