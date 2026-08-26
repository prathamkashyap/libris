package com.example.lms.event;

import com.example.lms.entity.AuditAction;
import com.example.lms.entity.AuditEntityType;
import org.springframework.context.ApplicationEvent;

public class EntityAuditEvent extends ApplicationEvent {

  private final AuditAction action;
  private final AuditEntityType entityType;
  private final Long entityId;
  private final String description;
  private final Long actorId;
  private final String actorUsername;
  private final String actorRole;
  private final String ipAddress;
  private final String userAgent;

  public EntityAuditEvent(
      Object source,
      AuditAction action,
      AuditEntityType entityType,
      Long entityId,
      String description,
      Long actorId,
      String actorUsername,
      String actorRole,
      String ipAddress,
      String userAgent) {
    super(source);
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.description = description;
    this.actorId = actorId;
    this.actorUsername = actorUsername;
    this.actorRole = actorRole;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
  }

  public AuditAction getAction() {
    return action;
  }

  public AuditEntityType getEntityType() {
    return entityType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public String getDescription() {
    return description;
  }

  public Long getActorId() {
    return actorId;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public String getActorRole() {
    return actorRole;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }
}
