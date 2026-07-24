package com.example.lms.repository;
import com.example.lms.entity.BorrowRecord; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface BorrowRecordRepository extends JpaRepository<BorrowRecord,Long> { List<BorrowRecord> findByReturnDateIsNull(); List<BorrowRecord> findByReturnDateIsNotNull(); boolean existsByBookId(Long bookId); boolean existsByStudentId(Long studentId); }
