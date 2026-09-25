/**
 * db_proposal_v2.test.js
 * 
 * Comprehensive Unit Test Suite testing all functions, calculations, 
 * constraints, and schema operations defined in DB_proposal_v2.sql and DB_init.sql.
 */

const { newDb } = require('pg-mem');
const assert = require('assert');
const {
    calculateRemainingSpots,
    calculateCapacityPercentage,
    determineEventAvailabilityStatus,
    countChangeRequestItems,
    getDashboardStatusDistribution,
    normalizeRoleCode,
    checkUserPermission,
    assignRoleToUser,
    assignPermissionToRole,
    createParticipant,
    backfillParticipantsFromUsers,
    backfillRegistrationsParticipantId,
    normalizeCategoryCode,
    validatePayment
} = require('./db_functions');

/**
 * Initializes in-memory PostgreSQL instance with DB_init.sql and DB_proposal_v2.sql schema
 */
function createTestDatabase() {
    const db = newDb();

    // Register replace function in pg-mem
    db.public.registerFunction({
        name: 'replace',
        args: ['text', 'text', 'text'],
        returns: 'text',
        implementation: (str, f, t) => str ? str.replaceAll(f, t) : str
    });

    // DB_init.sql
    db.public.none(`
        CREATE TABLE users (
            id SERIAL PRIMARY KEY,
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,
            email VARCHAR(255) UNIQUE NOT NULL,
            phone VARCHAR(20),
            password_hash VARCHAR(255) NOT NULL,
            role VARCHAR(50) NOT NULL,
            status VARCHAR(50) DEFAULT 'active',
            avatar TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE audit_logs (
            id SERIAL PRIMARY KEY,
            user_id INT REFERENCES users(id) ON DELETE SET NULL,
            action VARCHAR(255) NOT NULL,
            target_type VARCHAR(100) NOT NULL,
            target_id INT,
            description TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE events (
            id SERIAL PRIMARY KEY,
            title VARCHAR(255) NOT NULL,
            description TEXT,
            category VARCHAR(100),
            location TEXT,
            event_date DATE,
            start_time TIME,
            end_time TIME,
            max_participants INT,
            registration_deadline TIMESTAMP,
            image TEXT,
            rules TEXT,
            contact_email VARCHAR(255),
            eligibility TEXT,
            allow_cancel BOOLEAN DEFAULT TRUE,
            show_seats BOOLEAN DEFAULT TRUE,
            status VARCHAR(50) DEFAULT 'draft',
            created_by INT REFERENCES users(id) ON DELETE SET NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE event_admins (
            event_id INT REFERENCES events(id) ON DELETE CASCADE,
            user_id INT REFERENCES users(id) ON DELETE CASCADE,
            assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (event_id, user_id)
        );

        CREATE TABLE event_approvals (
            id SERIAL PRIMARY KEY,
            event_id INT REFERENCES events(id) ON DELETE CASCADE,
            reviewer_id INT REFERENCES users(id) ON DELETE SET NULL,
            status VARCHAR(50) NOT NULL,
            comment TEXT,
            reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE change_requests (
            id SERIAL PRIMARY KEY,
            event_id INT REFERENCES events(id) ON DELETE CASCADE,
            submitted_by INT REFERENCES users(id) ON DELETE SET NULL,
            status VARCHAR(50) DEFAULT 'pending',
            reason TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            reviewed_at TIMESTAMP
        );

        CREATE TABLE change_request_items (
            id SERIAL PRIMARY KEY,
            change_request_id INT REFERENCES change_requests(id) ON DELETE CASCADE,
            field_name VARCHAR(255) NOT NULL,
            old_value TEXT,
            new_value TEXT
        );

        CREATE TABLE registrations (
            id SERIAL PRIMARY KEY,
            user_id INT REFERENCES users(id) ON DELETE CASCADE,
            event_id INT REFERENCES events(id) ON DELETE CASCADE,
            status VARCHAR(50) DEFAULT 'registered',
            registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            cancelled_at TIMESTAMP
        );

        CREATE TABLE payments (
            id SERIAL PRIMARY KEY,
            registration_id INT REFERENCES registrations(id) ON DELETE CASCADE,
            amount NUMERIC NOT NULL,
            payment_method VARCHAR(100),
            payment_status VARCHAR(50) DEFAULT 'pending',
            transaction_reference VARCHAR(255),
            paid_at TIMESTAMP
        );
    `);

    // DB_proposal_v2.sql
    db.public.none(`
        CREATE TABLE mas_par (
            id              SERIAL PRIMARY KEY,
            user_id         INT UNIQUE REFERENCES users(id) ON DELETE SET NULL,
            first_name      VARCHAR(100) NOT NULL,
            last_name       VARCHAR(100) NOT NULL,
            email           VARCHAR(255),
            phone           VARCHAR(20),
            avatar          TEXT,
            organization    VARCHAR(255),
            student_id      VARCHAR(50),
            status          VARCHAR(50) NOT NULL DEFAULT 'active',
            created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT mas_par_status_valid CHECK (status IN ('active','inactive','merged'))
        );

        CREATE INDEX ix_mas_par_user  ON mas_par (user_id);
        CREATE INDEX ix_mas_par_email ON mas_par (lower(email));
        CREATE INDEX ix_mas_par_name  ON mas_par (last_name, first_name);

        ALTER TABLE registrations
            ADD COLUMN participant_id INT REFERENCES mas_par(id) ON DELETE CASCADE;

        CREATE TABLE roles (
            id           SERIAL PRIMARY KEY,
            code         VARCHAR(50)  NOT NULL UNIQUE,
            name         VARCHAR(100) NOT NULL,
            system_role  VARCHAR(50)  NOT NULL DEFAULT 'admin',
            scope        VARCHAR(100) NOT NULL DEFAULT 'ALL_EVENTS',
            status       VARCHAR(50)  NOT NULL DEFAULT 'active',
            is_builtin   BOOLEAN      NOT NULL DEFAULT FALSE,
            created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT roles_system_valid CHECK (system_role IN ('user','admin','super-admin')),
            CONSTRAINT roles_status_valid CHECK (status IN ('active','disabled'))
        );

        CREATE TABLE permissions (
            code  VARCHAR(50)  PRIMARY KEY,
            label VARCHAR(100) NOT NULL
        );

        INSERT INTO permissions (code, label) VALUES
            ('all-event',          'All Event'),
            ('event-registration', 'Event Registration'),
            ('my-registration',    'My Registration'),
            ('create-event',       'Create Event'),
            ('event-approvals',    'Event Approvals'),
            ('change-request',     'Change Request'),
            ('dashboard',          'Dashboard'),
            ('role-management',    'Role Management'),
            ('user-management',    'User Management');

        CREATE TABLE role_permissions (
            role_id         INT REFERENCES roles(id) ON DELETE CASCADE,
            permission_code VARCHAR(50) REFERENCES permissions(code) ON DELETE CASCADE,
            PRIMARY KEY (role_id, permission_code)
        );

        CREATE TABLE user_roles (
            user_id     INT REFERENCES users(id) ON DELETE CASCADE,
            role_id     INT REFERENCES roles(id) ON DELETE CASCADE,
            assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            assigned_by INT REFERENCES users(id) ON DELETE SET NULL,
            PRIMARY KEY (user_id, role_id)
        );

        INSERT INTO roles (code, name, system_role, is_builtin) VALUES
            ('user',        'User',        'user',        TRUE),
            ('admin',       'Admin',       'admin',       TRUE),
            ('super-admin', 'Super Admin', 'super-admin', TRUE);

        ALTER TABLE events
            ADD COLUMN price_amount NUMERIC NOT NULL DEFAULT 0,
            ADD COLUMN currency     CHAR(3)       NOT NULL DEFAULT 'THB',
            ADD COLUMN end_date     DATE,
            ADD COLUMN timezone     VARCHAR(64)   NOT NULL DEFAULT 'Asia/Bangkok',
            ADD COLUMN priority     VARCHAR(20)   NOT NULL DEFAULT 'standard',
            ADD COLUMN published_at TIMESTAMP,
            ADD COLUMN updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP;

        ALTER TABLE registrations
            ADD COLUMN registration_code   VARCHAR(50),
            ADD COLUMN attendee_name       VARCHAR(255),
            ADD COLUMN attendee_email      VARCHAR(255),
            ADD COLUMN attendee_phone      VARCHAR(20),
            ADD COLUMN consent_accepted_at TIMESTAMP,
            ADD COLUMN checked_in_at       TIMESTAMP;

        ALTER TABLE event_admins
            ADD COLUMN role_label VARCHAR(100) NOT NULL DEFAULT 'Event Admin';

        ALTER TABLE payments
            ADD COLUMN currency  CHAR(3) NOT NULL DEFAULT 'THB',
            ADD COLUMN qr_reference VARCHAR(255);

        ALTER TABLE audit_logs
            ADD COLUMN actor_name   VARCHAR(255),
            ADD COLUMN actor_role   VARCHAR(100),
            ADD COLUMN target_label VARCHAR(255),
            ADD COLUMN tag          VARCHAR(50);

        CREATE TABLE event_categories (
            id         SERIAL PRIMARY KEY,
            code       VARCHAR(50)  NOT NULL UNIQUE,
            name       VARCHAR(100) NOT NULL,
            sort_order INT          NOT NULL DEFAULT 0
        );

        INSERT INTO event_categories (code, name, sort_order) VALUES
            ('conference',   'Conference',           1),
            ('workshop',     'Workshop',             2),
            ('meetup',       'Meetup',               3),
            ('seminar',      'Seminar',              4),
            ('technology',   'Technology',           5),
            ('music',        'Music',                6),
            ('entertainment','Entertainment & Arts', 7),
            ('others',       'Others',               99);

        ALTER TABLE events
            ADD COLUMN category_id INT REFERENCES event_categories(id) ON DELETE SET NULL;

        ALTER TABLE registrations
            ADD CONSTRAINT ux_registration_once UNIQUE (event_id, participant_id);

        ALTER TABLE events
            ADD CONSTRAINT events_status_valid CHECK (
                status IN ('draft','pending_review','published','rejected','completed','cancelled')),
            ADD CONSTRAINT events_price_positive CHECK (price_amount >= 0),
            ADD CONSTRAINT events_capacity_positive CHECK (max_participants IS NULL OR max_participants > 0),
            ADD CONSTRAINT events_date_order CHECK (end_date IS NULL OR event_date IS NULL OR end_date >= event_date),
            ADD CONSTRAINT events_priority_valid CHECK (priority IN ('standard','high'));

        ALTER TABLE registrations
            ADD CONSTRAINT registrations_status_valid CHECK (
                status IN ('registered','cancelled','attended','no_show'));

        ALTER TABLE payments
            ADD CONSTRAINT payments_amount_positive CHECK (amount >= 0),
            ADD CONSTRAINT payments_status_valid CHECK (
                payment_status IN ('pending','paid','not_required','failed','refunded')),
            ADD CONSTRAINT payments_free_zero CHECK (
                payment_status <> 'not_required' OR amount = 0),
            ADD CONSTRAINT payments_paid_has_time CHECK (
                payment_status <> 'paid' OR paid_at IS NOT NULL);

        ALTER TABLE change_requests
            ADD CONSTRAINT change_requests_status_valid CHECK (status IN ('pending','approved','rejected'));

        ALTER TABLE event_approvals
            ADD CONSTRAINT event_approvals_status_valid CHECK (status IN ('pending','approved','rejected'));

        CREATE UNIQUE INDEX ux_change_request_pending
            ON change_requests (event_id) WHERE status = 'pending';
    `);

    return db;
}

/**
 * Runs the complete test suite and returns structured report data
 */
function runTestSuite(logger = console.log) {
    const db = createTestDatabase();
    const results = [];
    let passedCount = 0;
    let failedCount = 0;

    function test(group, name, fn) {
        const start = Date.now();
        try {
            fn();
            const durationMs = Date.now() - start;
            results.push({ group, name, status: 'PASSED', durationMs });
            logger(`  [PASS] ${name} (${durationMs}ms)`);
            passedCount++;
        } catch (err) {
            const durationMs = Date.now() - start;
            results.push({ group, name, status: 'FAILED', durationMs, error: err.message });
            logger(`  [FAIL] ${name} (${durationMs}ms): ${err.message}`);
            failedCount++;
        }
    }

    logger('================================================================================');
    logger('            UNIT TEST SUITE: DB_proposal_v2.sql FUNCTIONS & CONSTRAINTS');
    logger('================================================================================\n');

    // -------------------------------------------------------------------------
    // GROUP 1: Participant Identity Management (mas_par)
    // -------------------------------------------------------------------------
    logger('GROUP 1: Participant Master Data (mas_par) Functions');

    test('Group 1', 'createParticipant: creates account-linked participant', () => {
        db.public.none("INSERT INTO users (id, first_name, last_name, email, password_hash, role) VALUES (1, 'Alice', 'Wonderland', 'alice@test.com', 'p1', 'user');");
        const p = createParticipant(db, {
            user_id: 1,
            first_name: 'Alice',
            last_name: 'Wonderland',
            email: 'alice@test.com',
            phone: '0811111111'
        });
        assert.ok(p.id > 0);
        assert.strictEqual(p.user_id, 1);
        assert.strictEqual(p.first_name, 'Alice');
    });

    test('Group 1', 'createParticipant: creates walk-in participant with user_id = NULL', () => {
        const walkin = createParticipant(db, {
            user_id: null,
            first_name: 'WalkIn',
            last_name: 'Guest',
            email: 'walkin@test.com',
            phone: '0822222222',
            organization: 'Independent'
        });
        assert.ok(walkin.id > 0);
        assert.strictEqual(walkin.user_id, null);
        assert.strictEqual(walkin.organization, 'Independent');
    });

    test('Group 1', 'createParticipant: allows multiple walk-ins with NULL user_id', () => {
        const walkin2 = createParticipant(db, {
            user_id: null,
            first_name: 'WalkIn2',
            last_name: 'Guest2'
        });
        assert.ok(walkin2.id > 0);
        assert.strictEqual(walkin2.user_id, null);
    });

    test('Group 1', 'backfillParticipantsFromUsers: backfills existing users without participants', () => {
        db.public.none("INSERT INTO users (id, first_name, last_name, email, password_hash, role) VALUES (2, 'Bob', 'Marley', 'bob@test.com', 'p2', 'user');");
        backfillParticipantsFromUsers(db);
        const bob = db.public.one("SELECT * FROM mas_par WHERE user_id = 2;");
        assert.ok(bob !== null);
        assert.strictEqual(bob.first_name, 'Bob');
    });

    test('Group 1', 'backfillRegistrationsParticipantId: links participant_id to registrations', () => {
        db.public.none("INSERT INTO events (id, title, status) VALUES (1, 'Event 1', 'published');");
        db.public.none("INSERT INTO registrations (id, user_id, event_id, status) VALUES (1, 1, 1, 'registered');");
        backfillRegistrationsParticipantId(db);
        const reg = db.public.one("SELECT * FROM registrations WHERE id = 1;");
        assert.ok(reg.participant_id !== null);
    });

    test('Group 1', 'mas_par: status check constraint rejects invalid status', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO mas_par (first_name, last_name, status) VALUES ('Bad', 'Status', 'deleted');");
        });
    });


    // -------------------------------------------------------------------------
    // GROUP 2: Dynamic RBAC & Authorization Functions
    // -------------------------------------------------------------------------
    logger('\nGROUP 2: Dynamic RBAC & Authorization Functions');

    test('Group 2', 'normalizeRoleCode: handles spaces and underscores properly', () => {
        assert.strictEqual(normalizeRoleCode('Super Admin'), 'super-admin');
        assert.strictEqual(normalizeRoleCode('super_admin'), 'super-admin');
        assert.strictEqual(normalizeRoleCode('ADMIN'), 'admin');
        assert.strictEqual(normalizeRoleCode('User'), 'user');
    });

    test('Group 2', 'roles & permissions: seeds 3 roles and 9 permissions', () => {
        const roles = db.public.many("SELECT * FROM roles;");
        assert.strictEqual(roles.length, 3);
        const perms = db.public.many("SELECT * FROM permissions;");
        assert.strictEqual(perms.length, 9);
    });

    test('Group 2', 'assignRoleToUser: maps user to role in user_roles', () => {
        const adminRole = db.public.one("SELECT id FROM roles WHERE code = 'admin';");
        assignRoleToUser(db, 2, adminRole.id);
        const ur = db.public.one("SELECT * FROM user_roles WHERE user_id = 2;");
        assert.strictEqual(ur.role_id, adminRole.id);
    });

    test('Group 2', 'assignPermissionToRole: assigns permissions to a role', () => {
        const adminRole = db.public.one("SELECT id FROM roles WHERE code = 'admin';");
        assignPermissionToRole(db, adminRole.id, 'create-event');
        assignPermissionToRole(db, adminRole.id, 'all-event');
        const rolePerms = db.public.many(`SELECT * FROM role_permissions WHERE role_id = ${adminRole.id};`);
        assert.strictEqual(rolePerms.length, 2);
    });

    test('Group 2', 'checkUserPermission: returns true if user possesses permission', () => {
        const hasCreate = checkUserPermission(db, 2, 'create-event');
        assert.strictEqual(hasCreate, true);
    });

    test('Group 2', 'checkUserPermission: returns false if user lacks permission', () => {
        const hasUserMgmt = checkUserPermission(db, 2, 'user-management');
        assert.strictEqual(hasUserMgmt, false);
    });

    test('Group 2', 'roles: constraint roles_system_valid rejects invalid system_role', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO roles (code, name, system_role) VALUES ('test', 'Test', 'super_user');");
        });
    });


    // -------------------------------------------------------------------------
    // GROUP 3: Event Category Normalization & Lookup
    // -------------------------------------------------------------------------
    logger('\nGROUP 3: Event Category Functions');

    test('Group 3', 'normalizeCategoryCode: fixes "Entertainment & Arts" mapping defect', () => {
        assert.strictEqual(normalizeCategoryCode('Entertainment & Arts'), 'entertainment');
        assert.strictEqual(normalizeCategoryCode('technology'), 'technology');
        assert.strictEqual(normalizeCategoryCode('Conference'), 'conference');
        assert.strictEqual(normalizeCategoryCode('Unknown Category'), 'others');
    });

    test('Group 3', 'event_categories: seeds 8 standard categories', () => {
        const categories = db.public.many("SELECT * FROM event_categories;");
        assert.strictEqual(categories.length, 8);
    });

    test('Group 3', 'events: link category_id to event_categories', () => {
        const techCat = db.public.one("SELECT id FROM event_categories WHERE code = 'technology';");
        db.public.none(`UPDATE events SET category_id = ${techCat.id} WHERE id = 1;`);
        const ev = db.public.one("SELECT category_id FROM events WHERE id = 1;");
        assert.strictEqual(ev.category_id, techCat.id);
    });


    // -------------------------------------------------------------------------
    // GROUP 4: Database Integrity Constraints
    // -------------------------------------------------------------------------
    logger('\nGROUP 4: Data Integrity Constraints');

    test('Group 4', 'Constraint ux_registration_once: rejects duplicate registration for same participant', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO registrations (user_id, event_id, participant_id, status) VALUES (1, 1, 1, 'registered');");
        });
    });

    test('Group 4', 'Constraint events_status_valid: rejects invalid event status', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO events (title, status) VALUES ('Invalid Event', 'in_progress');");
        });
    });

    test('Group 4', 'Constraint events_price_positive: rejects negative price', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO events (title, price_amount) VALUES ('Bad Price', -50);");
        });
    });

    test('Group 4', 'Constraint events_capacity_positive: rejects capacity <= 0', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO events (title, max_participants) VALUES ('Zero Capacity', 0);");
        });
    });

    test('Group 4', 'Constraint events_date_order: rejects end_date earlier than event_date', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO events (title, event_date, end_date) VALUES ('Backward Dates', '2026-10-10', '2026-10-01');");
        });
    });

    test('Group 4', 'Constraint payments_free_zero: rejects not_required payment with amount > 0', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO payments (registration_id, amount, payment_status) VALUES (1, 100, 'not_required');");
        });
    });

    test('Group 4', 'Constraint payments_paid_has_time: rejects paid status without paid_at timestamp', () => {
        assert.throws(() => {
            db.public.none("INSERT INTO payments (registration_id, amount, payment_status, paid_at) VALUES (1, 100, 'paid', NULL);");
        });
    });

    test('Group 4', 'Constraint ux_change_request_pending: rejects multiple pending change requests for same event', () => {
        db.public.none("INSERT INTO change_requests (event_id, status) VALUES (1, 'pending');");
        assert.throws(() => {
            db.public.none("INSERT INTO change_requests (event_id, status) VALUES (1, 'pending');");
        });
    });


    // -------------------------------------------------------------------------
    // GROUP 5: Calculated Business Functions (DB_proposal_v2.sql lines 298-308)
    // -------------------------------------------------------------------------
    logger('\nGROUP 5: Calculated Business Functions');

    // Seed events for calculations
    db.public.none(`
        INSERT INTO events (id, title, max_participants, event_date, status) VALUES
            (10, 'Full Event', 2, '2026-10-10', 'published'),
            (11, '80% Full Event', 10, '2026-10-15', 'published'),
            (12, 'Empty Event', 50, '2026-10-20', 'published'),
            (13, 'Unlimited Event', NULL, '2026-10-25', 'published'),
            (14, 'Draft Event', 20, '2026-10-30', 'draft'),
            (15, 'Past Event', 20, '2026-09-01', 'published');

        INSERT INTO mas_par (id, first_name, last_name) VALUES
            (10, 'P10', 'Test'),
            (11, 'P11', 'Test'),
            (12, 'P12', 'Test'),
            (13, 'P13', 'Test');

        -- Event 10: 2/2 full
        INSERT INTO registrations (id, event_id, participant_id, status) VALUES
            (101, 10, 10, 'registered'),
            (102, 10, 11, 'attended');

        -- Event 11: 8/10 spots taken (80% full)
        INSERT INTO registrations (id, event_id, participant_id, status) VALUES
            (103, 11, 10, 'registered'),
            (104, 11, 11, 'registered'),
            (105, 11, 12, 'cancelled'); -- cancelled does not count as active
    `);

    test('Group 5', 'calculateRemainingSpots: computes max_participants - active registrations', () => {
        assert.strictEqual(calculateRemainingSpots(db, 10), 0); // 2 - 2 = 0
        assert.strictEqual(calculateRemainingSpots(db, 11), 8); // 10 - 2 active = 8
        assert.strictEqual(calculateRemainingSpots(db, 12), 50); // 50 - 0 = 50
        assert.strictEqual(calculateRemainingSpots(db, 13), null); // unlimited
    });

    test('Group 5', 'calculateCapacityPercentage: computes (active / max) * 100', () => {
        assert.strictEqual(calculateCapacityPercentage(db, 10), 100); // 2/2 = 100%
        assert.strictEqual(calculateCapacityPercentage(db, 11), 20); // 2/10 = 20%
        assert.strictEqual(calculateCapacityPercentage(db, 12), 0); // 0/50 = 0%
    });

    test('Group 5', 'determineEventAvailabilityStatus: evaluates dynamic status', () => {
        assert.strictEqual(determineEventAvailabilityStatus(db, 10, '2026-09-25'), 'closed'); // 0 spots remaining
        assert.strictEqual(determineEventAvailabilityStatus(db, 11, '2026-09-25'), 'open'); // 20% full
        assert.strictEqual(determineEventAvailabilityStatus(db, 14, '2026-09-25'), 'draft'); // draft status
        assert.strictEqual(determineEventAvailabilityStatus(db, 15, '2026-09-25'), 'completed'); // past event
    });

    test('Group 5', 'countChangeRequestItems: counts number of modified fields', () => {
        db.public.none("INSERT INTO change_requests (id, event_id, status) VALUES (50, 1, 'approved');");
        db.public.none(`
            INSERT INTO change_request_items (change_request_id, field_name, old_value, new_value) VALUES
                (50, 'title', 'Old Title', 'New Title'),
                (50, 'price_amount', '0', '150'),
                (50, 'max_participants', '50', '100');
        `);
        assert.strictEqual(countChangeRequestItems(db, 50), 3);
    });

    test('Group 5', 'getDashboardStatusDistribution: aggregates events count by status', () => {
        const dist = getDashboardStatusDistribution(db);
        assert.ok(dist.published >= 5);
        assert.ok(dist.draft >= 1);
    });


    // -------------------------------------------------------------------------
    // GROUP 6: Payment Rule Validation Function
    // -------------------------------------------------------------------------
    logger('\nGROUP 6: Payment Validation Rule Functions');

    test('Group 6', 'validatePayment: succeeds on valid paid transaction', () => {
        const res = validatePayment(250, 'paid', '2026-09-25T14:30:00Z');
        assert.strictEqual(res.valid, true);
    });

    test('Group 6', 'validatePayment: succeeds on valid free event payment', () => {
        const res = validatePayment(0, 'not_required', null);
        assert.strictEqual(res.valid, true);
    });

    test('Group 6', 'validatePayment: fails when amount is negative', () => {
        const res = validatePayment(-20, 'pending', null);
        assert.strictEqual(res.valid, false);
    });

    test('Group 6', 'validatePayment: fails when not_required has amount > 0', () => {
        const res = validatePayment(100, 'not_required', null);
        assert.strictEqual(res.valid, false);
    });

    test('Group 6', 'validatePayment: fails when paid status is missing paid_at', () => {
        const res = validatePayment(500, 'paid', null);
        assert.strictEqual(res.valid, false);
    });

    logger('\n================================================================================');
    logger(`TOTAL TESTS: ${results.length} | PASSED: ${passedCount} | FAILED: ${failedCount}`);
    logger('================================================================================\n');

    return {
        total: results.length,
        passed: passedCount,
        failed: failedCount,
        results
    };
}

module.exports = {
    createTestDatabase,
    runTestSuite
};

if (require.main === module) {
    runTestSuite();
}
