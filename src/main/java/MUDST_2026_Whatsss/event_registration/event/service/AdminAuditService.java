package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;
import MUDST_2026_Whatsss.event_registration.auth.domain.LoginAttempt;
import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthSessionRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.LoginAttemptRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.ParticipantRepository;
import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import MUDST_2026_Whatsss.event_registration.event.repository.AuditLogRepository;
import MUDST_2026_Whatsss.event_registration.event.web.dto.AuditLogResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.LoginAttemptResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.SessionActivityResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminAuditService {

    private static final DateTimeFormatter EXPORT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.of("Asia/Bangkok"));
    private static final Instant EARLIEST_AUDIT_TIME = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant LATEST_AUDIT_TIME = Instant.parse("3000-01-01T00:00:00Z");
    private static final String[] EXPORT_HEADERS = {
            "Timestamp", "Actor name", "Actor email", "Action", "Target type",
            "Target", "Outcome", "Metadata"
    };
    private static final String[] SIGN_IN_EXPORT_HEADERS = {
            "Timestamp", "User name", "User email", "Outcome", "Failure reason",
            "IP address", "User agent"
    };
    private static final String[] SESSION_EXPORT_HEADERS = {
            "Activity timestamp", "User name", "User email", "State", "IP address",
            "User agent", "Created at", "Expires at", "Revoked at"
    };

    private final AuditLogRepository auditRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthUserRepository userRepository;
    private final ParticipantRepository participantRepository;

    public AdminAuditService(
            AuditLogRepository auditRepository,
            LoginAttemptRepository loginAttemptRepository,
            AuthSessionRepository sessionRepository,
            AuthUserRepository userRepository,
            ParticipantRepository participantRepository) {
        this.auditRepository = auditRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> logs(String query, String targetType, Pageable pageable) {
        return logs(query, targetType, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> logs(
            String query,
            String targetType,
            Instant from,
            Instant to,
            Pageable pageable) {
        Page<AuditLog> logs = auditRepository.findForAdministration(
                blankToEmpty(query), upperOrEmpty(targetType), lowerBound(from), upperBound(to), pageable);
        Map<UUID, Participant> profiles = profilesFor(logs.getContent());
        return logs.map(log -> toResponse(log,
                log.getActor() == null ? null : profiles.get(log.getActor().getUserId())));
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(String query, String targetType, Instant from, Instant to) {
        List<AuditLog> logs = auditRepository.findAllForAdministration(
                blankToEmpty(query), upperOrEmpty(targetType), lowerBound(from), upperBound(to));
        Map<UUID, Participant> profiles = profilesFor(logs);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Audit Logs");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            for (int column = 0; column < EXPORT_HEADERS.length; column++) {
                header.createCell(column).setCellValue(EXPORT_HEADERS[column]);
                header.getCell(column).setCellStyle(headerStyle);
            }

            for (int index = 0; index < logs.size(); index++) {
                AuditLog log = logs.get(index);
                Participant participant = log.getActor() == null
                        ? null : profiles.get(log.getActor().getUserId());
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(EXPORT_DATE_FORMAT.format(log.getCreatedAt()));
                row.createCell(1).setCellValue(log.getActor() == null ? "System" : displayName(log.getActor(), participant));
                row.createCell(2).setCellValue(log.getActor() == null ? "" : log.getActor().getEmail());
                row.createCell(3).setCellValue(log.getAction());
                row.createCell(4).setCellValue(log.getTargetType());
                row.createCell(5).setCellValue(valueOrEmpty(log.getTargetLabel()));
                row.createCell(6).setCellValue(log.getOutcome());
                row.createCell(7).setCellValue(String.valueOf(log.getMetadata()));
            }

            sheet.createFreezePane(0, 1);
            if (!logs.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, logs.size(), 0, EXPORT_HEADERS.length - 1));
            }
            for (int column = 0; column < EXPORT_HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 768, 16_000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate the audit log workbook.", exception);
        }
    }

    @Transactional(readOnly = true)
    public Page<LoginAttemptResponse> loginAttempts(
            String query, String outcome, Instant from, Instant to, Pageable pageable) {
        Page<LoginAttempt> attempts = loginAttemptRepository.findForAdministration(
                blankToEmpty(query), upperOrEmpty(outcome), lowerBound(from), upperBound(to), pageable);
        UserDirectory directory = userDirectory(attempts.getContent().stream()
                .map(LoginAttempt::getUserId).filter(id -> id != null).toList());
        return attempts.map(attempt -> toResponse(attempt, directory));
    }

    @Transactional(readOnly = true)
    public byte[] exportLoginAttemptsExcel(String query, String outcome, Instant from, Instant to) {
        List<LoginAttempt> attempts = loginAttemptRepository.findAllForAdministration(
                blankToEmpty(query), upperOrEmpty(outcome), lowerBound(from), upperBound(to));
        UserDirectory directory = userDirectory(attempts.stream()
                .map(LoginAttempt::getUserId).filter(id -> id != null).toList());
        return workbook("Sign-in Activity", SIGN_IN_EXPORT_HEADERS, attempts.size(), (sheet, index) -> {
            LoginAttempt attempt = attempts.get(index);
            UserIdentity user = identity(attempt.getUserId(), directory);
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(EXPORT_DATE_FORMAT.format(attempt.getAttemptedAt()));
            row.createCell(1).setCellValue(user == null ? "Unknown account" : user.displayName());
            row.createCell(2).setCellValue(user == null ? "" : user.email());
            row.createCell(3).setCellValue(attempt.getOutcome());
            row.createCell(4).setCellValue(valueOrEmpty(attempt.getFailureReason()));
            row.createCell(5).setCellValue(valueOrEmpty(attempt.getIpAddress()));
            row.createCell(6).setCellValue(valueOrEmpty(attempt.getUserAgent()));
        });
    }

    @Transactional(readOnly = true)
    public Page<SessionActivityResponse> sessions(
            String query, String state, Instant from, Instant to, Pageable pageable) {
        Instant now = Instant.now();
        Page<AuthSession> sessions = sessionRepository.findForAdministration(
                blankToEmpty(query), upperOrEmpty(state), now, lowerBound(from), upperBound(to), pageable);
        UserDirectory directory = userDirectory(sessions.getContent().stream()
                .map(AuthSession::getUserId).toList());
        return sessions.map(session -> toResponse(session, directory, now));
    }

    @Transactional(readOnly = true)
    public byte[] exportSessionsExcel(String query, String state, Instant from, Instant to) {
        Instant now = Instant.now();
        List<AuthSession> sessions = sessionRepository.findAllForAdministration(
                blankToEmpty(query), upperOrEmpty(state), now, lowerBound(from), upperBound(to));
        UserDirectory directory = userDirectory(sessions.stream().map(AuthSession::getUserId).toList());
        return workbook("Session Activity", SESSION_EXPORT_HEADERS, sessions.size(), (sheet, index) -> {
            AuthSession session = sessions.get(index);
            UserIdentity user = identity(session.getUserId(), directory);
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(EXPORT_DATE_FORMAT.format(activityAt(session)));
            row.createCell(1).setCellValue(user == null ? "Unknown user" : user.displayName());
            row.createCell(2).setCellValue(user == null ? "" : user.email());
            row.createCell(3).setCellValue(sessionState(session, now));
            row.createCell(4).setCellValue(valueOrEmpty(session.getIpAddress()));
            row.createCell(5).setCellValue(valueOrEmpty(session.getUserAgent()));
            row.createCell(6).setCellValue(EXPORT_DATE_FORMAT.format(session.getCreatedAt()));
            row.createCell(7).setCellValue(EXPORT_DATE_FORMAT.format(session.getExpiresAt()));
            row.createCell(8).setCellValue(session.getRevokedAt() == null
                    ? "" : EXPORT_DATE_FORMAT.format(session.getRevokedAt()));
        });
    }

    private Map<UUID, Participant> profilesFor(List<AuditLog> logs) {
        List<UUID> actorIds = logs.stream().map(AuditLog::getActor)
                .filter(actor -> actor != null).map(AuthUser::getUserId).distinct().toList();
        return actorIds.isEmpty() ? Map.of()
                : participantRepository.findByUserIdIn(actorIds).stream()
                        .collect(Collectors.toMap(Participant::getUserId, Function.identity()));
    }

    private UserDirectory userDirectory(List<UUID> ids) {
        Set<UUID> distinctIds = new LinkedHashSet<>(ids);
        if (distinctIds.isEmpty()) return new UserDirectory(Map.of(), Map.of());
        List<UUID> userIds = List.copyOf(distinctIds);
        Map<UUID, AuthUser> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AuthUser::getUserId, Function.identity()));
        Map<UUID, Participant> profiles = participantRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(Participant::getUserId, Function.identity()));
        return new UserDirectory(users, profiles);
    }

    private static LoginAttemptResponse toResponse(LoginAttempt attempt, UserDirectory directory) {
        UserIdentity user = identity(attempt.getUserId(), directory);
        LoginAttemptResponse.UserSummary summary = user == null ? null
                : new LoginAttemptResponse.UserSummary(user.userId(), user.email(), user.displayName());
        return new LoginAttemptResponse(
                attempt.getLoginAttemptId(), summary, attempt.getOutcome(), attempt.getFailureReason(),
                attempt.getIpAddress(), attempt.getUserAgent(), attempt.getAttemptedAt());
    }

    private static SessionActivityResponse toResponse(
            AuthSession session, UserDirectory directory, Instant now) {
        UserIdentity user = identity(session.getUserId(), directory);
        SessionActivityResponse.UserSummary summary = user == null ? null
                : new SessionActivityResponse.UserSummary(user.userId(), user.email(), user.displayName());
        return new SessionActivityResponse(
                session.getSessionId(), summary, sessionState(session, now), session.getIpAddress(),
                session.getUserAgent(), session.getCreatedAt(), session.getExpiresAt(),
                session.getRevokedAt(), activityAt(session));
    }

    private static UserIdentity identity(UUID userId, UserDirectory directory) {
        if (userId == null) return null;
        AuthUser user = directory.users().get(userId);
        if (user == null) return null;
        return new UserIdentity(userId, user.getEmail(), displayName(user, directory.profiles().get(userId)));
    }

    private static String sessionState(AuthSession session, Instant now) {
        if (session.getRevokedReason() != null) return session.getRevokedReason();
        return session.getExpiresAt().isAfter(now) ? "ACTIVE" : "EXPIRED";
    }

    private static Instant activityAt(AuthSession session) {
        return session.getRevokedAt() == null ? session.getCreatedAt() : session.getRevokedAt();
    }

    private static byte[] workbook(
            String sheetName, String[] headers, int rowCount, SheetRowWriter writer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
                header.getCell(column).setCellStyle(headerStyle);
            }
            for (int index = 0; index < rowCount; index++) writer.write(sheet, index);
            sheet.createFreezePane(0, 1);
            if (rowCount > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowCount, 0, headers.length - 1));
            }
            for (int column = 0; column < headers.length; column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 768, 16_000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate the security activity workbook.", exception);
        }
    }

    private static CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
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

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Instant lowerBound(Instant value) {
        return value == null ? EARLIEST_AUDIT_TIME : value;
    }

    private static Instant upperBound(Instant value) {
        return value == null ? LATEST_AUDIT_TIME : value;
    }

    @FunctionalInterface
    private interface SheetRowWriter {
        void write(Sheet sheet, int index);
    }

    private record UserDirectory(Map<UUID, AuthUser> users, Map<UUID, Participant> profiles) {
    }

    private record UserIdentity(UUID userId, String email, String displayName) {
    }
}
