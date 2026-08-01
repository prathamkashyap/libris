package com.example.lms.repository;

import com.example.lms.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
  @EntityGraph(attributePaths = {"account"})
  Optional<StudentProfile> findByAccountUsername(String username);

  @EntityGraph(attributePaths = {"account"})
  Page<StudentProfile> findAll(Pageable pageable);

  @Query(
      "SELECT s FROM StudentProfile s LEFT JOIN FETCH s.account WHERE LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(s.email) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(s.account.username) LIKE LOWER(CONCAT('%',:q,'%'))")
  Page<StudentProfile> search(@Param("q") String q, Pageable pageable);
}
