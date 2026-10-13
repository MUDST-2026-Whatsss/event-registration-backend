package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Query(value = """
            select a from LoginAttempt a
            where (:query = ''
                   or lower(coalesce(a.failureReason, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(a.userAgent, '')) like lower(concat('%', :query, '%'))
                   or exists (select u.userId from AuthUser u where u.userId = a.userId
                              and lower(u.email) like lower(concat('%', :query, '%')))
                   or exists (select p.participantId from Participant p where p.userId = a.userId
                              and (lower(coalesce(p.firstName, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(p.lastName, '')) like lower(concat('%', :query, '%'))
                                   or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')))
                                      like lower(concat('%', :query, '%')))))
              and (:outcome = '' or a.outcome = :outcome)
              and a.attemptedAt >= :fromAttemptedAt and a.attemptedAt < :toAttemptedAt
            order by a.attemptedAt desc
            """,
            countQuery = """
            select count(a.loginAttemptId) from LoginAttempt a
            where (:query = ''
                   or lower(coalesce(a.failureReason, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(a.userAgent, '')) like lower(concat('%', :query, '%'))
                   or exists (select u.userId from AuthUser u where u.userId = a.userId
                              and lower(u.email) like lower(concat('%', :query, '%')))
                   or exists (select p.participantId from Participant p where p.userId = a.userId
                              and (lower(coalesce(p.firstName, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(p.lastName, '')) like lower(concat('%', :query, '%'))
                                   or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')))
                                      like lower(concat('%', :query, '%')))))
              and (:outcome = '' or a.outcome = :outcome)
              and a.attemptedAt >= :fromAttemptedAt and a.attemptedAt < :toAttemptedAt
            """)
    Page<LoginAttempt> findForAdministration(
            @Param("query") String query,
            @Param("outcome") String outcome,
            @Param("fromAttemptedAt") Instant fromAttemptedAt,
            @Param("toAttemptedAt") Instant toAttemptedAt,
            Pageable pageable);

    @Query("""
            select a from LoginAttempt a
            where (:query = ''
                   or lower(coalesce(a.failureReason, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(a.userAgent, '')) like lower(concat('%', :query, '%'))
                   or exists (select u.userId from AuthUser u where u.userId = a.userId
                              and lower(u.email) like lower(concat('%', :query, '%')))
                   or exists (select p.participantId from Participant p where p.userId = a.userId
                              and (lower(coalesce(p.firstName, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(p.lastName, '')) like lower(concat('%', :query, '%'))
                                   or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')))
                                      like lower(concat('%', :query, '%')))))
              and (:outcome = '' or a.outcome = :outcome)
              and a.attemptedAt >= :fromAttemptedAt and a.attemptedAt < :toAttemptedAt
            order by a.attemptedAt desc
            """)
    List<LoginAttempt> findAllForAdministration(
            @Param("query") String query,
            @Param("outcome") String outcome,
            @Param("fromAttemptedAt") Instant fromAttemptedAt,
            @Param("toAttemptedAt") Instant toAttemptedAt);
}
