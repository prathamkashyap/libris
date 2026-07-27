package com.example.lms.repository;
import com.example.lms.entity.StudentProfile; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.util.Optional;
public interface StudentProfileRepository extends JpaRepository<StudentProfile,Long> {
    Optional<StudentProfile> findByAccountUsername(String username);
    @Query("SELECT s FROM StudentProfile s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(s.email) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(s.account.username) LIKE LOWER(CONCAT('%',:q,'%'))")
    org.springframework.data.domain.Page<StudentProfile> search(@Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
