package com.example.lms.controller;

import com.example.lms.dto.AuditLogResponse;
import com.example.lms.entity.AuditAction;
import com.example.lms.entity.AuditEntityType;
import com.example.lms.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController @RequestMapping("/api/audit") @Tag(name="Audit Log",description="Entity change audit trail")
public class AuditController {

    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }

    @GetMapping @Operation(summary="List audit logs",description="Paginated audit trail with optional filters by action, entity type, actor, or date range.")
    public Page<AuditLogResponse> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) String actorUsername,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(action, entityType, actorUsername, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/{id}") @Operation(summary="Get audit log entry by ID")
    public AuditLogResponse get(@PathVariable Long id) { return service.get(id); }
}
