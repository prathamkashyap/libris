package com.example.lms.repository;

import com.example.lms.entity.Newspaper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewspaperRepository extends JpaRepository<Newspaper, Long> {
  @Query(
      "SELECT n FROM Newspaper n WHERE LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.publisher) LIKE LOWER(CONCAT('%', :query, '%'))")
  Page<Newspaper> searchNewspapers(@Param("query") String query, Pageable pageable);
}
