# Unit Tests for DB_proposal_v2.sql Functions & Schema

This directory contains the unit test suite and runner for all functions, calculations, and data constraints defined in `DB_proposal_v2.sql`.

## Directory Structure

```text
Unit_test/
├── db_functions.js           # Implementations of business & calculated functions
├── db_proposal_v2.test.js    # Comprehensive unit test suite
├── run_tests.js              # Node.js runner script exporting logs to Result/
├── run_tests.bat             # Windows Batch runner
├── run_tests.ps1             # PowerShell runner
├── package.json              # NPM dependencies (pg-mem)
└── Result/                   # Exported test logs & execution summaries
    ├── latest_test_results.log
    ├── test_run_<timestamp>.log
    └── test_summary.md
```

## Functions Tested

1. **Calculated Business Functions (DB_proposal_v2.sql: 298-308)**:
   - `calculateRemainingSpots(db, eventId)`
   - `calculateCapacityPercentage(db, eventId)`
   - `determineEventAvailabilityStatus(db, eventId, currentDate)`
   - `countChangeRequestItems(db, changeRequestId)`
   - `getDashboardStatusDistribution(db)`

2. **RBAC & Authorization Functions (DB_proposal_v2.sql: 84-151)**:
   - `normalizeRoleCode(roleString)`
   - `checkUserPermission(db, userId, permissionCode)`
   - `assignRoleToUser(db, userId, roleId)`
   - `assignPermissionToRole(db, roleId, permissionCode)`

3. **Participant Identity Management Functions (DB_proposal_v2.sql: 14-81)**:
   - `createParticipant(db, data)` (Account-linked & Walk-in `user_id = NULL`)
   - `backfillParticipantsFromUsers(db)`
   - `backfillRegistrationsParticipantId(db)`

4. **Event Category Lookup & Bug Fixes (DB_proposal_v2.sql: 205-230)**:
   - `normalizeCategoryCode(categoryStr)` (Fixes 'Entertainment & Arts' matching defect)

5. **Payment Validation Rule Functions (DB_proposal_v2.sql: 253-263)**:
   - `validatePayment(amount, status, paidAt)`

6. **Integrity Constraints & Triggers**:
   - `ux_registration_once`
   - `events_status_valid`, `events_price_positive`, `events_capacity_positive`, `events_date_order`, `events_priority_valid`
   - `registrations_status_valid`
   - `payments_amount_positive`, `payments_status_valid`, `payments_free_zero`, `payments_paid_has_time`
   - `ux_change_request_pending`

## How to Run

### Option 1: Using Node.js
```bash
cd Unit_test
node run_tests.js
```

### Option 2: Using Batch script (Windows CMD)
```cmd
cd Unit_test
run_tests.bat
```

### Option 3: Using PowerShell
```powershell
cd Unit_test
.\run_tests.ps1
```

All test outputs will be displayed on screen and automatically archived into `Unit_test/Result/`.

