package com.example.lms.service;

import com.example.lms.dto.AuditLogResponse;
import com.example.lms.entity.AuditAction;
import com.example.lms.entity.AuditEntityType;
import com.example.lms.entity.AuditLog;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.AuditLogRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  private final AuditLogRepository repository;

  public AuditService(AuditLogRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public AuditLog log(
      Instant timestamp,
      Long actorId,
      String actorUsername,
      String actorRole,
      AuditAction action,
      AuditEntityType entityType,
      Long entityId,
      String description,
      String ipAddress,
      String userAgent) {
    var entry = new AuditLog();
    entry.setTimestamp(timestamp);
    entry.setActorId(actorId);
    entry.setActorUsername(actorUsername);
    entry.setActorRole(actorRole);
    entry.setAction(action);
    entry.setEntityType(entityType);
    entry.setEntityId(entityId);
    entry.setDescription(description);
    entry.setIpAddress(ipAddress);
    entry.setUserAgent(userAgent);
    return repository.save(entry);
  }

  @Transactional(readOnly = true)
  public Page<AuditLogResponse> list(
      AuditAction action,
      AuditEntityType entityType,
      String actorUsername,
      Instant from,
      Instant to,
      Pageable pageable) {
    return repository
        .findByFilters(action, entityType, actorUsername, from, to, pageable)
        .map(this::response);
  }

  @Transactional(readOnly = true)
  public AuditLogResponse get(Long id) {
    return response(
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Audit log entry %d was not found.".formatted(id))));
  }

  private AuditLogResponse response(AuditLog log) {
    return new AuditLogResponse(
        log.getId(),
        log.getTimestamp(),
        log.getActorId(),
        log.getActorUsername(),
        log.getActorRole(),
        log.getAction(),
        log.getEntityType(),
        log.getEntityId(),
        log.getDescription(),
        log.getIpAddress(),
        log.getUserAgent());
  }
}
