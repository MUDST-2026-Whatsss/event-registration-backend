package MUDST_2026_Whatsss.event_registration.event.web;

import MUDST_2026_Whatsss.event_registration.event.service.AdminAuditService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAuditController {

    private final AdminAuditService service;

    public SuperAdminAuditController(AdminAuditService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AuditLogResponse> logs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String targetType,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.logs(query, targetType, pageable);
    }
}
