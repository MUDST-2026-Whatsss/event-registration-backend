package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query(value = """
            select a from AuditLog a
            left join fetch a.actor actor
            where (:query = ''
                   or lower(a.action) like lower(concat('%', :query, '%'))
                   or lower(coalesce(a.targetLabel, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(actor.email, '')) like lower(concat('%', :query, '%')))
              and (:targetType = '' or a.targetType = :targetType)
            """,
            countQuery = """
            select count(a.auditLogId) from AuditLog a
            left join a.actor actor
            where (:query = ''
                   or lower(a.action) like lower(concat('%', :query, '%'))
                   or lower(coalesce(a.targetLabel, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(actor.email, '')) like lower(concat('%', :query, '%')))
              and (:targetType = '' or a.targetType = :targetType)
            """)
    Page<AuditLog> findForAdministration(
            @Param("query") String query,
            @Param("targetType") String targetType,
            Pageable pageable);
}
