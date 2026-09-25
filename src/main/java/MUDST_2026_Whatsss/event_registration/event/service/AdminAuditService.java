package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.repository.ParticipantRepository;
import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import MUDST_2026_Whatsss.event_registration.event.repository.AuditLogRepository;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminAuditService {

    private final AuditLogRepository auditRepository;
    private final ParticipantRepository participantRepository;

    public AdminAuditService(
            AuditLogRepository auditRepository,
            ParticipantRepository participantRepository) {
        this.auditRepository = auditRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> logs(String query, String targetType, Pageable pageable) {
        Page<AuditLog> logs = auditRepository.findForAdministration(
                blankToEmpty(query), upperOrEmpty(targetType), pageable);
        List<UUID> actorIds = logs.getContent().stream().map(AuditLog::getActor)
                .filter(actor -> actor != null).map(AuthUser::getUserId).distinct().toList();
        Map<UUID, Participant> profiles = actorIds.isEmpty() ? Map.of()
                : participantRepository.findByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(Participant::getUserId, Function.identity()));
        return logs.map(log -> toResponse(log,
                log.getActor() == null ? null : profiles.get(log.getActor().getUserId())));
    }

    private static AuditLogResponse toResponse(AuditLog log, Participant participant) {
        AuthUser actor = log.getActor();
        AuditLogResponse.UserSummary summary = actor == null ? null : new AuditLogResponse.UserSummary(
                actor.getUserId(), actor.getEmail(), displayName(actor, participant));
        return new AuditLogResponse(
                log.getAuditLogId(), summary, log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getTargetLabel(), log.getOutcome(), log.getMetadata(),
                log.getCreatedAt());
    }

    private static String displayName(AuthUser user, Participant participant) {
        if (participant == null) return user.getEmail();
        String name = String.join(" ",
                participant.getFirstName() == null ? "" : participant.getFirstName().trim(),
                participant.getLastName() == null ? "" : participant.getLastName().trim()).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String upperOrEmpty(String value) {
        return blankToEmpty(value).toUpperCase();
    }
}
