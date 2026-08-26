package com.example.lms.repository;

import com.example.lms.entity.AuditAction;
import com.example.lms.entity.AuditEntityType;
import com.example.lms.entity.AuditLog;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  @Query(
      "SELECT a FROM AuditLog a WHERE "
          + "(:action IS NULL OR a.action = :action) AND "
          + "(:entityType IS NULL OR a.entityType = :entityType) AND "
          + "(:actorUsername IS NULL OR a.actorUsername LIKE %:actorUsername%) AND "
          + "(:from IS NULL OR a.timestamp >= :from) AND "
          + "(:to IS NULL OR a.timestamp <= :to) "
          + "ORDER BY a.timestamp DESC")
  Page<AuditLog> findByFilters(
      @Param("action") AuditAction action,
      @Param("entityType") AuditEntityType entityType,
      @Param("actorUsername") String actorUsername,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
