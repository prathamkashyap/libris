package com.example.lms.repository;
import com.example.lms.entity.LibrarianProfile; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;
public interface LibrarianProfileRepository extends JpaRepository<LibrarianProfile,Long> {
    @Query("SELECT l FROM LibrarianProfile l WHERE LOWER(l.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(l.account.username) LIKE LOWER(CONCAT('%',:q,'%'))")
    org.springframework.data.domain.Page<LibrarianProfile> search(@Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
