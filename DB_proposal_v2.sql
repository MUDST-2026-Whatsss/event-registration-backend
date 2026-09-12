-- =====================================================================
--  ข้อเสนอส่วนเพิ่มเติม — ต่อยอดจาก DB_init.sql (ไม่แก้ของเดิม)
--
--  ที่มา: อ่าน frontend ทั้ง 25 หน้าจอแล้วเทียบกับ DB_init.sql
--  DB_init.sql ครอบคลุมโครงหลักได้ดีแล้ว (9 ตาราง ตรงกับ entity ที่ UI ใช้)
--  ไฟล์นี้เติมเฉพาะส่วนที่ UI ต้องใช้แต่ยังไม่มี
--
--  รันต่อจาก DB_init.sql:  psql -d <db> -f DB_proposal_v2.sql
--  ทดสอบแล้วบน PostgreSQL 17.7
-- =====================================================================


-- =====================================================================
--  ส่วนที่ 1 — mas_par: แยกตัวบุคคลออกจาก account
-- =====================================================================
--
--  ทำไมต้องแยก:
--  ตอนนี้ users เก็บทั้ง "บัญชีล็อกอิน" (email/password/role) และ
--  "ตัวบุคคล" (first_name/last_name/phone) ปนกัน ซึ่งพอระบบโตจะติด 3 เรื่อง
--
--  1. ผู้เข้าร่วมที่ไม่มีบัญชี — walk-in หรือคนที่ให้เพื่อนลงทะเบียนให้
--     ตอนนี้ registrations.user_id ผูกกับ users ตรงๆ จึงต้องสร้าง account
--     (พร้อม password_hash) ให้ทุกคน ทั้งที่เขาไม่เคยล็อกอิน
--
--  2. ลงทะเบียนแทนคนอื่น — 1 บัญชีจองให้หลายคน
--     UI มีเค้าอยู่แล้ว: EventRegistrationView กรอก fullName/phone/email
--     ได้เองโดยไม่ต้องตรงกับโปรไฟล์ที่ล็อกอินอยู่
--
--  3. ข้อมูลย้อนหลังเพี้ยน — ถ้าคนเปลี่ยนนามสกุล รายชื่อผู้เข้าร่วม
--     ของงานที่จบไปแล้วจะเปลี่ยนตาม ทั้งที่ควรเก็บค่า ณ วันที่สมัคร
--
--  โครงที่เสนอ: users = ล็อกอิน, mas_par = ตัวบุคคล, เชื่อมกันแบบ 0..1 : 1
--  (mas_par หนึ่งแถวจะมี account หรือไม่มีก็ได้)

CREATE TABLE mas_par (
    id              SERIAL PRIMARY KEY,
    -- NULL = ผู้เข้าร่วมที่ยังไม่มีบัญชี (walk-in / ถูกลงทะเบียนแทน)
    user_id         INT UNIQUE REFERENCES users(id) ON DELETE SET NULL,

    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    avatar          TEXT,

    -- เผื่อระบบใหญ่: แยกสังกัด/รุ่น เอาไว้ทำรายงานและกรองสิทธิ์
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

COMMENT ON TABLE  mas_par IS 'Master data: ตัวบุคคล แยกจาก account ล็อกอิน (users)';
COMMENT ON COLUMN mas_par.user_id IS 'NULL = ผู้เข้าร่วมที่ไม่มีบัญชีในระบบ';

-- ย้ายข้อมูลคนที่มีอยู่แล้วให้เป็น mas_par (idempotent)
INSERT INTO mas_par (user_id, first_name, last_name, email, phone, avatar)
SELECT u.id, u.first_name, u.last_name, u.email, u.phone, u.avatar
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM mas_par p WHERE p.user_id = u.id);

-- registrations ชี้ที่ "ตัวบุคคล" ส่วน user_id เดิมคือ "บัญชีที่กดสมัคร"
ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS participant_id INT REFERENCES mas_par(id) ON DELETE CASCADE;

UPDATE registrations r
SET participant_id = p.id
FROM mas_par p
WHERE p.user_id = r.user_id AND r.participant_id IS NULL;

COMMENT ON COLUMN registrations.user_id IS 'บัญชีที่ทำรายการ (อาจสมัครแทนคนอื่น)';
COMMENT ON COLUMN registrations.participant_id IS 'ตัวบุคคลที่เข้าร่วมงานจริง';


-- =====================================================================
--  ส่วนที่ 2 — RBAC: role/permission เป็นข้อมูล ไม่ใช่ string
-- =====================================================================
--
--  ตอนนี้ users.role เป็น VARCHAR(50) เดี่ยวๆ แต่หน้าจอต้องการมากกว่านั้น:
--  - SuperAdminRoleCreate.vue สร้าง role ใหม่ได้เอง + ติ๊ก permission 9 ตัว
--    และมีช่อง department (ขอบเขตของ role)
--  - SuperAdminUsers.vue แสดง permission ต่อ user เป็น list
--  ถ้าเก็บเป็น string เดียว หน้าพวกนี้ทำงานไม่ได้

CREATE TABLE roles (
    id           SERIAL PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    system_role  VARCHAR(50)  NOT NULL DEFAULT 'admin',
    scope        VARCHAR(100) NOT NULL DEFAULT 'ALL_EVENTS',  -- ช่อง department
    status       VARCHAR(50)  NOT NULL DEFAULT 'active',
    is_builtin   BOOLEAN      NOT NULL DEFAULT FALSE,          -- กันลบ role พื้นฐาน
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT roles_system_valid CHECK (system_role IN ('user','admin','super-admin')),
    CONSTRAINT roles_status_valid CHECK (status IN ('active','disabled'))
);

CREATE TABLE permissions (
    code  VARCHAR(50)  PRIMARY KEY,
    label VARCHAR(100) NOT NULL
);

-- 9 ตัวตรงตาม SuperAdminRoleCreate.vue:13-23
INSERT INTO permissions (code, label) VALUES
    ('all-event',          'All Event'),
    ('event-registration', 'Event Registration'),
    ('my-registration',    'My Registration'),
    ('create-event',       'Create Event'),
    ('event-approvals',    'Event Approvals'),
    ('change-request',     'Change Request'),
    ('dashboard',          'Dashboard'),
    ('role-management',    'Role Management'),
    ('user-management',    'User Management')
ON CONFLICT (code) DO NOTHING;

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
    ('super-admin', 'Super Admin', 'super-admin', TRUE)
ON CONFLICT (code) DO NOTHING;

-- ย้าย users.role เดิมเข้า user_roles (คงคอลัมน์เดิมไว้ ไม่ทำ API พัง)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = lower(replace(u.role, ' ', '-'))
ON CONFLICT DO NOTHING;


-- =====================================================================
--  ส่วนที่ 3 — คอลัมน์ที่หน้าจอต้องใช้แต่ยังไม่มี
-- =====================================================================

-- ราคา: หน้า public แสดงราคาทุกงาน (0-899 บาท) และมี PromptPay QR
-- แต่ events ยังไม่มีคอลัมน์ราคา  (0 = ฟรี)
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS price_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency     CHAR(3)       NOT NULL DEFAULT 'THB',
    -- data/events.js มีงานข้ามวัน เช่น "Oct 15 - 17, 2026"
    ADD COLUMN IF NOT EXISTS end_date     DATE,
    ADD COLUMN IF NOT EXISTS timezone     VARCHAR(64)   NOT NULL DEFAULT 'Asia/Bangkok',
    -- approvals.js มี priority: high | standard
    ADD COLUMN IF NOT EXISTS priority     VARCHAR(20)   NOT NULL DEFAULT 'standard',
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- snapshot ผู้เข้าร่วม ณ วันที่สมัคร (ฟอร์มแก้ได้ ไม่ผูกกับโปรไฟล์ปัจจุบัน)
-- + QR check-in ที่ MyRegistrationsView ใช้
ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS registration_code   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS attendee_name       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attendee_email      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attendee_phone      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS consent_accepted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS checked_in_at       TIMESTAMP;

-- EventAdminsModal แสดงตำแหน่งต่อคน (Event Admin / Organizer / Coordinator)
-- คนละเรื่องกับ system role
ALTER TABLE event_admins
    ADD COLUMN IF NOT EXISTS role_label VARCHAR(100) NOT NULL DEFAULT 'Event Admin';

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS currency  CHAR(3) NOT NULL DEFAULT 'THB',
    -- QR payload: PAY-<eventId>-<timestamp>
    ADD COLUMN IF NOT EXISTS qr_reference VARCHAR(255);

-- audit_logs: หน้าจอแสดงชื่อ+role ของผู้กระทำ และ tag สี
-- เก็บเป็น snapshot เพราะถ้า user ถูกลบ (ON DELETE SET NULL) ประวัติต้องยังอ่านได้
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS actor_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS actor_role   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS target_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tag          VARCHAR(50);


-- =====================================================================
--  ส่วนที่ 4 — lookup table (category / venue)
-- =====================================================================
--  ฟอร์มมี conference/workshop/meetup แต่ข้อมูลจริงมี
--  Seminar/Technology/Music/Entertainment & Arts/Others → ต้องเป็นตาราง

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
    ('others',       'Others',               99)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS category_id INT REFERENCES event_categories(id) ON DELETE SET NULL;

UPDATE events e
SET category_id = c.id
FROM event_categories c
WHERE lower(e.category) = c.code AND e.category_id IS NULL;


-- =====================================================================
--  ส่วนที่ 5 — constraint กันข้อมูลผิด (ตอนนี้ยังไม่มีเลย)
-- =====================================================================

-- useRegistrations.register() ใช้วิธี reactivate แถวเดิมที่ cancel แล้ว
-- ไม่ได้สร้างแถวใหม่ → 1 คน ต่อ 1 งาน ได้แถวเดียว
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
    -- งานฟรีต้องยอด 0
    ADD CONSTRAINT payments_free_zero CHECK (
        payment_status <> 'not_required' OR amount = 0),
    -- จ่ายแล้วต้องมีเวลาจ่าย
    ADD CONSTRAINT payments_paid_has_time CHECK (
        payment_status <> 'paid' OR paid_at IS NOT NULL);

ALTER TABLE change_requests
    ADD CONSTRAINT change_requests_status_valid CHECK (status IN ('pending','approved','rejected'));

ALTER TABLE event_approvals
    ADD CONSTRAINT event_approvals_status_valid CHECK (status IN ('pending','approved','rejected'));

-- มีคำขอค้างได้ครั้งละ 1 ต่องาน
CREATE UNIQUE INDEX ux_change_request_pending
    ON change_requests (event_id) WHERE status = 'pending';


-- =====================================================================
--  ส่วนที่ 6 — index ตามการใช้งานจริงของแต่ละหน้า
-- =====================================================================

-- หน้า public: ลิสต์งานที่เผยแพร่ เรียงตามวัน
CREATE INDEX ix_events_published ON events (event_date) WHERE status = 'published';
CREATE INDEX ix_events_status    ON events (status);
CREATE INDEX ix_events_creator   ON events (created_by);
CREATE INDEX ix_events_category  ON events (category_id);

-- นับยอดลงทะเบียนต่องาน (ใช้เกือบทุกหน้า)
CREATE INDEX ix_reg_event_active ON registrations (event_id)
    WHERE status IN ('registered','attended');
CREATE INDEX ix_reg_participant  ON registrations (participant_id);
CREATE INDEX ix_reg_user         ON registrations (user_id);

CREATE INDEX ix_event_admins_user ON event_admins (user_id);
CREATE INDEX ix_audit_recent      ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_target      ON audit_logs (target_type, target_id);
CREATE INDEX ix_approvals_pending ON event_approvals (reviewed_at DESC) WHERE status = 'pending';
CREATE INDEX ix_payments_reg      ON payments (registration_id);


-- =====================================================================
--  หมายเหตุ: ค่าที่ "ห้ามเก็บ" เพราะต้องคำนวณ
-- =====================================================================
--   450/500, 90%          -> COUNT(registrations) / events.max_participants
--   "42 spots remaining"  -> max_participants - COUNT(registrations)
--   open/almost-full/closed/upcoming -> คำนวณจากที่ว่าง + วันที่
--   "2 fields changed"    -> COUNT(change_request_items)
--   กราฟสัดส่วนใน Dashboard -> GROUP BY events.status
--
--  ถ้าเก็บค่าพวกนี้ลงตาราง จะเพี้ยนทันทีที่มีคนสมัครเพิ่ม
