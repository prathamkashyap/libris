package com.example.lms.repository;

import com.example.lms.entity.LibrarianProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibrarianProfileRepository extends JpaRepository<LibrarianProfile, Long> {
  @EntityGraph(attributePaths = {"account"})
  Page<LibrarianProfile> findAll(Pageable pageable);

  @Query(
      "SELECT l FROM LibrarianProfile l LEFT JOIN FETCH l.account WHERE LOWER(l.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(l.account.username) LIKE LOWER(CONCAT('%',:q,'%'))")
  Page<LibrarianProfile> search(@Param("q") String q, Pageable pageable);
}
