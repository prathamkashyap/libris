package com.example.lms.dto;

import com.example.lms.entity.AuditAction;
import com.example.lms.entity.AuditEntityType;

import java.time.Instant;

public record AuditLogResponse(
    Long id,
    Instant timestamp,
    Long actorId,
    String actorUsername,
    String actorRole,
    AuditAction action,
    AuditEntityType entityType,
    Long entityId,
    String description,
    String ipAddress,
    String userAgent
) {}
