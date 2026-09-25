# Test Execution Report - DB Proposal v2 Functions

- **Date**: 9/25/2026, 11:13:01 PM
- **Duration**: 120 ms
- **Total Tests**: 34
- **Passed**: 34
- **Failed**: 0
- **Status**: ✅ ALL TESTS PASSED

## Detailed Test Results

| Group | Test Case | Status | Duration | Error |
| --- | --- | --- | --- | --- |
| Group 1 | createParticipant: creates account-linked participant | ✅ PASS | 6ms | - |
| Group 1 | createParticipant: creates walk-in participant with user_id = NULL | ✅ PASS | 2ms | - |
| Group 1 | createParticipant: allows multiple walk-ins with NULL user_id | ✅ PASS | 2ms | - |
| Group 1 | backfillParticipantsFromUsers: backfills existing users without participants | ✅ PASS | 4ms | - |
| Group 1 | backfillRegistrationsParticipantId: links participant_id to registrations | ✅ PASS | 5ms | - |
| Group 1 | mas_par: status check constraint rejects invalid status | ✅ PASS | 1ms | - |
| Group 2 | normalizeRoleCode: handles spaces and underscores properly | ✅ PASS | 0ms | - |
| Group 2 | roles & permissions: seeds 3 roles and 9 permissions | ✅ PASS | 1ms | - |
| Group 2 | assignRoleToUser: maps user to role in user_roles | ✅ PASS | 1ms | - |
| Group 2 | assignPermissionToRole: assigns permissions to a role | ✅ PASS | 3ms | - |
| Group 2 | checkUserPermission: returns true if user possesses permission | ✅ PASS | 2ms | - |
| Group 2 | checkUserPermission: returns false if user lacks permission | ✅ PASS | 2ms | - |
| Group 2 | roles: constraint roles_system_valid rejects invalid system_role | ✅ PASS | 1ms | - |
| Group 3 | normalizeCategoryCode: fixes "Entertainment & Arts" mapping defect | ✅ PASS | 0ms | - |
| Group 3 | event_categories: seeds 8 standard categories | ✅ PASS | 0ms | - |
| Group 3 | events: link category_id to event_categories | ✅ PASS | 2ms | - |
| Group 4 | Constraint ux_registration_once: rejects duplicate registration for same participant | ✅ PASS | 1ms | - |
| Group 4 | Constraint events_status_valid: rejects invalid event status | ✅ PASS | 0ms | - |
| Group 4 | Constraint events_price_positive: rejects negative price | ✅ PASS | 1ms | - |
| Group 4 | Constraint events_capacity_positive: rejects capacity <= 0 | ✅ PASS | 0ms | - |
| Group 4 | Constraint events_date_order: rejects end_date earlier than event_date | ✅ PASS | 3ms | - |
| Group 4 | Constraint payments_free_zero: rejects not_required payment with amount > 0 | ✅ PASS | 0ms | - |
| Group 4 | Constraint payments_paid_has_time: rejects paid status without paid_at timestamp | ✅ PASS | 0ms | - |
| Group 4 | Constraint ux_change_request_pending: rejects multiple pending change requests for same event | ✅ PASS | 2ms | - |
| Group 5 | calculateRemainingSpots: computes max_participants - active registrations | ✅ PASS | 8ms | - |
| Group 5 | calculateCapacityPercentage: computes (active / max) * 100 | ✅ PASS | 2ms | - |
| Group 5 | determineEventAvailabilityStatus: evaluates dynamic status | ✅ PASS | 9ms | - |
| Group 5 | countChangeRequestItems: counts number of modified fields | ✅ PASS | 3ms | - |
| Group 5 | getDashboardStatusDistribution: aggregates events count by status | ✅ PASS | 1ms | - |
| Group 6 | validatePayment: succeeds on valid paid transaction | ✅ PASS | 0ms | - |
| Group 6 | validatePayment: succeeds on valid free event payment | ✅ PASS | 0ms | - |
| Group 6 | validatePayment: fails when amount is negative | ✅ PASS | 0ms | - |
| Group 6 | validatePayment: fails when not_required has amount > 0 | ✅ PASS | 0ms | - |
| Group 6 | validatePayment: fails when paid status is missing paid_at | ✅ PASS | 0ms | - |

## Output Log Files
- Timestamped Log: `test_run_20260925_231301.log`
- Latest Log: `latest_test_results.log`