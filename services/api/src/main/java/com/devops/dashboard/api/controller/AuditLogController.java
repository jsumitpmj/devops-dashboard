package com.devops.dashboard.api.controller;

import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditRepo;

    public AuditLogController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin')")
    public ResponseEntity<?> list(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        List<AuditLog> logs;

        if (entityType != null && entityId != null) {
            logs = auditRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        } else if (actorId != null) {
            logs = auditRepo.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
        } else if (action != null) {
            logs = auditRepo.findByActionOrderByCreatedAtDesc(action, pageable);
        } else {
            logs = auditRepo.findAll(pageable).getContent();
        }

        return ResponseEntity.ok(Map.of("data", logs, "total", logs.size(),
                "page", page, "pageSize", size));
    }
}
