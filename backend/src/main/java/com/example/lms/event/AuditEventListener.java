package com.example.lms.event;

import com.example.lms.service.AuditService;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {

  private final AuditService auditService;

  public AuditEventListener(AuditService auditService) {
    this.auditService = auditService;
  }

  @EventListener
  public void handleEntityAudit(EntityAuditEvent event) {
    auditService.log(
        Instant.now(),
        event.getActorId(),
        event.getActorUsername(),
        event.getActorRole(),
        event.getAction(),
        event.getEntityType(),
        event.getEntityId(),
        event.getDescription(),
        event.getIpAddress(),
        event.getUserAgent());
  }
}
