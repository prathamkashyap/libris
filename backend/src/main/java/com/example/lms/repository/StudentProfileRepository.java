package com.example.lms.repository;
import com.example.lms.entity.StudentProfile; import org.springframework.data.jpa.repository.JpaRepository;
public interface StudentProfileRepository extends JpaRepository<StudentProfile,Long> { }
