package com.example.lms.repository;
import com.example.lms.entity.Account; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountRepository extends JpaRepository<Account,Long> { Optional<Account> findByUsername(String username); boolean existsByUsername(String username); }
