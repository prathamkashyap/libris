package com.example.lms.repository;
import com.example.lms.entity.StudentProfile; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface StudentProfileRepository extends JpaRepository<StudentProfile,Long> {
    Optional<StudentProfile> findByAccountUsername(String username);
}
