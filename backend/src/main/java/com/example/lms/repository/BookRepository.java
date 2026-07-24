package com.example.lms.repository;
import com.example.lms.entity.Book; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface BookRepository extends JpaRepository<Book,Long> { List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title,String author); boolean existsByIsbn(String isbn); boolean existsByIsbnAndIdNot(String isbn,Long id); long countByAvailable(boolean available); }
