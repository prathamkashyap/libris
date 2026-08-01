package com.example.lms.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(name = "actor_username", length = 50)
  private String actorUsername;

  @Column(name = "actor_role", length = 20)
  private String actorRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 30)
  private AuditEntityType entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  public Long getId() {
    return id;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant v) {
    timestamp = v;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long v) {
    actorId = v;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public void setActorUsername(String v) {
    actorUsername = v;
  }

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String v) {
    actorRole = v;
  }

  public AuditAction getAction() {
    return action;
  }

  public void setAction(AuditAction v) {
    action = v;
  }

  public AuditEntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(AuditEntityType v) {
    entityType = v;
  }

  public Long getEntityId() {
    return entityId;
  }

  public void setEntityId(Long v) {
    entityId = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String v) {
    ipAddress = v;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String v) {
    userAgent = v;
  }
}
