/**
 * db_functions.js
 * 
 * Functional implementation of all business logic, calculations, and 
 * data operations specified in DB_proposal_v2.sql and DB_init.sql.
 */

// -------------------------------------------------------------
// 1. Calculated Business Functions (DB_proposal_v2.sql lines 298-308)
// -------------------------------------------------------------

/**
 * Calculates remaining spots for an event:
 * spots_remaining = max_participants - COUNT(active registrations)
 * @param {object} db - pg-mem database instance
 * @param {number} eventId - event ID
 * @returns {number|null} remaining seats or null if unlimited
 */
function calculateRemainingSpots(db, eventId) {
    const res = db.public.one(`
        SELECT e.max_participants,
               COUNT(CASE WHEN r.status IN ('registered', 'attended') THEN 1 END) AS active_count
        FROM events e
        LEFT JOIN registrations r ON r.event_id = e.id
        WHERE e.id = ${eventId}
        GROUP BY e.max_participants;
    `);
    if (!res) throw new Error(`Event with id ${eventId} not found`);
    if (res.max_participants === null || res.max_participants === undefined) return null;
    const active = Number(res.active_count);
    return Math.max(0, res.max_participants - active);
}

/**
 * Calculates capacity percentage: (active registrations / max_participants) * 100
 * @param {object} db - pg-mem database instance
 * @param {number} eventId - event ID
 * @returns {number} percentage (0-100+)
 */
function calculateCapacityPercentage(db, eventId) {
    const res = db.public.one(`
        SELECT e.max_participants,
               COUNT(CASE WHEN r.status IN ('registered', 'attended') THEN 1 END) AS active_count
        FROM events e
        LEFT JOIN registrations r ON r.event_id = e.id
        WHERE e.id = ${eventId}
        GROUP BY e.max_participants;
    `);
    if (!res) throw new Error(`Event with id ${eventId} not found`);
    if (!res.max_participants || res.max_participants <= 0) return 0;
    return Math.round((Number(res.active_count) / res.max_participants) * 100);
}

/**
 * Determines dynamic event availability status:
 * - 'draft' / 'pending_review' / 'rejected' / 'cancelled' -> returns status
 * - 'completed' / past date -> 'completed'
 * - remaining <= 0 -> 'closed'
 * - capacity >= 80% -> 'almost-full'
 * - otherwise -> 'open'
 * @param {object} db - pg-mem database instance
 * @param {number} eventId - event ID
 * @param {string} [currentDateStr] - current date ISO string (default: current date)
 * @returns {string} availability status
 */
function determineEventAvailabilityStatus(db, eventId, currentDateStr = '2026-09-25') {
    const res = db.public.one(`
        SELECT e.id, e.status, e.max_participants, e.event_date,
               COUNT(CASE WHEN r.status IN ('registered', 'attended') THEN 1 END) AS active_count
        FROM events e
        LEFT JOIN registrations r ON r.event_id = e.id
        WHERE e.id = ${eventId}
        GROUP BY e.id, e.status, e.max_participants, e.event_date;
    `);
    if (!res) throw new Error(`Event with id ${eventId} not found`);
    
    // Non-published states take precedence
    if (res.status !== 'published') {
        return res.status;
    }

    // Check date expiration
    if (res.event_date) {
        const evDateStr = typeof res.event_date === 'string' 
            ? res.event_date.slice(0, 10) 
            : new Date(res.event_date).toISOString().slice(0, 10);
        if (evDateStr < currentDateStr) {
            return 'completed';
        }
    }

    const active = Number(res.active_count);
    if (res.max_participants !== null && res.max_participants !== undefined) {
        const remaining = res.max_participants - active;
        if (remaining <= 0) return 'closed';
        const percent = (active / res.max_participants) * 100;
        if (percent >= 80) return 'almost-full';
    }

    return 'open';
}

/**
 * Counts modified fields for a change request: COUNT(change_request_items)
 * @param {object} db - pg-mem database instance
 * @param {number} changeRequestId - change request ID
 * @returns {number} count of changed fields
 */
function countChangeRequestItems(db, changeRequestId) {
    const res = db.public.one(`
        SELECT COUNT(*) AS changed_count
        FROM change_request_items
        WHERE change_request_id = ${changeRequestId};
    `);
    return Number(res ? res.changed_count : 0);
}

/**
 * Aggregates event counts grouped by status for Dashboard
 * @param {object} db - pg-mem database instance
 * @returns {Record<string, number>} distribution of events by status
 */
function getDashboardStatusDistribution(db) {
    const rows = db.public.many(`
        SELECT status, COUNT(*) AS count
        FROM events
        GROUP BY status;
    `);
    const distribution = {};
    for (const row of rows) {
        distribution[row.status] = Number(row.count);
    }
    return distribution;
}


// -------------------------------------------------------------
// 2. RBAC & Authorization Functions (DB_proposal_v2.sql lines 84-151)
// -------------------------------------------------------------

/**
 * Normalizes legacy role string to role code
 * Handles both spaces and underscores (e.g. 'Super Admin' -> 'super-admin', 'super_admin' -> 'super-admin')
 * @param {string} roleString
 * @returns {string} normalized role code
 */
function normalizeRoleCode(roleString) {
    if (!roleString) return '';
    return roleString.trim().toLowerCase().replace(/[\s_]+/g, '-');
}

/**
 * Checks if a user has a specific permission via user_roles -> roles -> role_permissions
 * @param {object} db
 * @param {number} userId
 * @param {string} permissionCode
 * @returns {boolean} true if user has permission
 */
function checkUserPermission(db, userId, permissionCode) {
    const rows = db.public.many(`
        SELECT rp.permission_code
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        JOIN role_permissions rp ON rp.role_id = r.id
        WHERE ur.user_id = ${userId}
          AND r.status = 'active'
          AND rp.permission_code = '${permissionCode}';
    `);
    return rows.length > 0;
}

/**
 * Assigns a role to a user
 * @param {object} db
 * @param {number} userId
 * @param {number} roleId
 * @param {number|null} assignedBy
 */
function assignRoleToUser(db, userId, roleId, assignedBy = null) {
    const assignedByVal = assignedBy === null ? 'NULL' : assignedBy;
    db.public.none(`
        INSERT INTO user_roles (user_id, role_id, assigned_by)
        VALUES (${userId}, ${roleId}, ${assignedByVal})
        ON CONFLICT (user_id, role_id) DO NOTHING;
    `);
}

/**
 * Assigns a permission to a role
 * @param {object} db
 * @param {number} roleId
 * @param {string} permissionCode
 */
function assignPermissionToRole(db, roleId, permissionCode) {
    db.public.none(`
        INSERT INTO role_permissions (role_id, permission_code)
        VALUES (${roleId}, '${permissionCode}')
        ON CONFLICT (role_id, permission_code) DO NOTHING;
    `);
}


// -------------------------------------------------------------
// 3. Participant Master Data (mas_par) Functions (DB_proposal_v2.sql lines 35-81)
// -------------------------------------------------------------

/**
 * Creates or retrieves a participant record in mas_par
 * Supports walk-ins with userId = null
 * @param {object} db
 * @param {object} p
 * @returns {object} created participant
 */
function createParticipant(db, p) {
    const userIdVal = p.user_id === null || p.user_id === undefined ? 'NULL' : p.user_id;
    const emailVal = p.email ? `'${p.email}'` : 'NULL';
    const phoneVal = p.phone ? `'${p.phone}'` : 'NULL';
    const statusVal = p.status ? `'${p.status}'` : "'active'";
    const orgVal = p.organization ? `'${p.organization}'` : 'NULL';

    db.public.none(`
        INSERT INTO mas_par (user_id, first_name, last_name, email, phone, organization, status)
        VALUES (${userIdVal}, '${p.first_name}', '${p.last_name}', ${emailVal}, ${phoneVal}, ${orgVal}, ${statusVal});
    `);
    
    return db.public.one(`
        SELECT * FROM mas_par 
        WHERE first_name = '${p.first_name}' AND last_name = '${p.last_name}'
        ORDER BY id DESC LIMIT 1;
    `);
}

/**
 * Backfills participants from existing users table (Idempotent)
 * @param {object} db
 */
function backfillParticipantsFromUsers(db) {
    db.public.none(`
        INSERT INTO mas_par (user_id, first_name, last_name, email, phone, avatar)
        SELECT users.id, users.first_name, users.last_name, users.email, users.phone, users.avatar
        FROM users
        WHERE users.id NOT IN (SELECT p.user_id FROM mas_par p WHERE p.user_id IS NOT NULL);
    `);
}

/**
 * Links registrations to participant_id using user_id mapping
 * @param {object} db
 */
function backfillRegistrationsParticipantId(db) {
    db.public.none(`
        UPDATE registrations
        SET participant_id = p.id
        FROM mas_par p
        WHERE p.user_id = registrations.user_id AND registrations.participant_id IS NULL;
    `);
}


// -------------------------------------------------------------
// 4. Category Normalization & Matching (DB_proposal_v2.sql lines 205-230)
// -------------------------------------------------------------

/**
 * Normalizes UI category string to code in event_categories
 * Fixes the bug in DB_proposal_v2.sql line 229 where 'Entertainment & Arts' fails to match 'entertainment'
 * @param {string} categoryStr
 * @returns {string} category code
 */
function normalizeCategoryCode(categoryStr) {
    if (!categoryStr) return 'others';
    const cleaned = categoryStr.trim().toLowerCase();
    if (cleaned.includes('entertainment') || cleaned.includes('arts')) return 'entertainment';
    if (cleaned.includes('tech')) return 'technology';
    if (cleaned.includes('conf')) return 'conference';
    if (cleaned.includes('work')) return 'workshop';
    if (cleaned.includes('meet')) return 'meetup';
    if (cleaned.includes('sem')) return 'seminar';
    if (cleaned.includes('music')) return 'music';
    return 'others';
}


// -------------------------------------------------------------
// 5. Payment Validation Rules (DB_proposal_v2.sql lines 253-263)
// -------------------------------------------------------------

/**
 * Validates payment entity fields according to schema constraints
 * @param {number} amount
 * @param {string} paymentStatus
 * @param {string|null} paidAt
 * @returns {{ valid: boolean, error?: string }}
 */
function validatePayment(amount, paymentStatus, paidAt) {
    if (amount < 0) {
        return { valid: false, error: 'Amount must be >= 0' };
    }
    const validStatuses = ['pending', 'paid', 'not_required', 'failed', 'refunded'];
    if (!validStatuses.includes(paymentStatus)) {
        return { valid: false, error: `Invalid payment_status: ${paymentStatus}` };
    }
    if (paymentStatus === 'not_required' && amount !== 0) {
        return { valid: false, error: 'Payment status not_required must have amount = 0' };
    }
    if (paymentStatus === 'paid' && !paidAt) {
        return { valid: false, error: 'Payment status paid must include paid_at timestamp' };
    }
    return { valid: true };
}

module.exports = {
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
};
