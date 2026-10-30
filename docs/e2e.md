# คู่มือ E2E Backend

คงขั้นตอนทดสอบของ GitHub เป็นหลักและรวมเฉพาะส่วนที่ยังขาดไว้ใน branch feat/registration-check-in
เปลี่ยนหัวข้อเคสให้เป็น snake_case และชื่อฟังก์ชันตรงกัน โดยใช้โครงสร้าง cypress/e2e และ cypress/support เดิม

## คำสั่ง

พิมพ์จากโฟลเดอร์ event-registration หลัง npm ci

```powershell
npm run test:e2e
npm run login
npm run login_failed
npm run register
npm run profile_update
npm run e2e:list
npm run e2e -- registration_consent_required
npm run e2e:open -- login
```

ชื่อหลักอย่าง login หรือ register เลือกทั้งกลุ่ม ส่วน login_failed เลือกเคสเดียว
คำสั่งกลาง npm run e2e -- ชื่อกลุ่มหรือชื่อเคส อ่านชื่อจากไฟล์อัตโนมัติ
ชื่อไม่ถูกต้อง ไม่มี test หรือมี test ไม่ผ่านจะคืนสถานะล้มเหลว
case ID เดียวอาจอยู่ในกลุ่มไฟล์และกลุ่มฟังก์ชันได้ แต่ full suite รันเคสจริงครั้งเดียว

## การเตรียมระบบและขอบเขต

Backend เรียก HTTP และฐานข้อมูลจริง ต้องใช้ Compose project และฐานข้อมูลทดสอบแยก
คัดลอก .env.example เป็น .env.e2e แล้วแทน secret ตามคู่มือ Docker ตั้ง RATE_LIMIT_ENABLED=false,
LOCKOUT_MAX_ATTEMPTS=5, MAIL_USER= และ MAIL_PASSWORD= ในไฟล์ทดสอบ
เลือกพอร์ตที่ว่าง เช่น API_PORT=18080, POSTGRES_PORT=15432, MINIO_API_PORT=19000, MINIO_CONSOLE_PORT=19001

```powershell
$env:COMPOSE_PROJECT_NAME='event-registration-e2e'
$env:COMPOSE_ENV_FILES="$PWD/.env.e2e"
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build --wait postgres minio api
$env:CYPRESS_BASE_URL='http://127.0.0.1:18080'
npm run test:e2e
```

COMPOSE_PROJECT_NAME ต้องกำหนดให้ตรงกันทั้งตอนเปิดบริการและตอนรัน Cypress เพราะ task ให้ Role
จะใช้ Docker CLI กับ PostgreSQL ของ project นี้โดยตรง เฉพาะบัญชี E2E ที่กำหนดไว้
บน Windows ใช้ Node task ส่ง argument แยกกัน จึงไม่พึ่ง quote แบบ Bash
ถ้าติดตั้ง Docker ไว้นอก PATH ใช้ DOCKER_CLI_PATH ชี้ไปที่ executable จริงได้

ชุด Java unit/integration ยังรันแยกด้วย `.\mvnw.cmd verify` และต้องเปิด Docker
API Cypress นี้ไม่ยืนยันการส่งอีเมล SMTP ถึงกล่องจดหมายจริงหรือพฤติกรรม rate limit 429

การชำระเงินเป็น QR จำลองตามขอบเขตโครงงาน ไม่มีการเก็บเงินจริง
เคสใหม่เตรียมข้อมูลเองหรือผ่าน beforeEach เพื่อให้เลือกมารันเดี่ยวได้

## รายการเคสใน branch นี้

| ไฟล์ใต้ cypress/e2e | ชื่อเคส |
| --- | --- |
| `01-health.cy.js` | `health`, `health_actuator` |
| `02-csrf-and-security.cy.js` | `security_csrf_token`, `security_csrf_missing`, `security_login_required` |
| `03-auth-lifecycle.cy.js` | `login`, `login_failed` |
| `04-event-categories.cy.js` | `events_categories` |
| `05-event-administration.cy.js` | `event_lifecycle`, `event_reject`, `event_publish_change`, `event_invalid_schedule` |
| `06-registration-and-check-in.cy.js` | `registration_check_in`, `registration_event_cancel`, `registration_login_required`, `guest_registration_link` |
| `07-access-control.cy.js` | `access_admin_denied`, `access_super_admin_denied`, `super_admin_access`, `role_create_delete` |
| `change_password.cy.js` | `change_password`, `change_password_failed`, `change_password_weak` |
| `events.cy.js` | `events_not_found` |
| `forgot_password.cy.js` | `forgot_password`, `forgot_password_invalid_email` |
| `health.cy.js` | `health_wrong_method` |
| `login.cy.js` | `login_unknown_account`, `login_normalized_email`, `login_empty`, `login_locked` |
| `logout.cy.js` | `logout_without_session` |
| `profile.cy.js` | `profile_update`, `profile_partial_update`, `profile_invalid_phone`, `profile_name_too_long` |
| `register.cy.js` | `register_duplicate`, `register_invalid_email`, `register_invalid_phone`, `register_weak_password`, `register_empty_name`, `register_name_too_long` |
| `registration.cy.js` | `registration_empty_list`, `registration_not_found`, `registration_missing_event`, `registration_consent_required`, `registration_cancel_missing` |
| `reset_password.cy.js` | `reset_password_invalid_token`, `reset_password_weak` |
| `security.cy.js` | `security_csrf_invalid`, `security_guest_denied` |
| `session.cy.js` | `session_refresh`, `session_refresh_missing`, `session_role_denied`, `session_select_role` |

## การรวมกับงานในเครื่องเดิม

เพิ่มเคสที่ยังขาด 35 เคส โดยไม่นำเคสเดิมในเครื่อง 15 เคสที่ซ้ำหรือหมดความจำเป็นมารันซ้ำ
สำหรับเส้นทางที่ซ้ำกันแต่มีจุดตรวจย่อยเพิ่มเติม จะเพิ่ม assertion เข้าเคสของ GitHub แทน

ใช้ cypress/support/scenarios.js ลงทะเบียนเฉพาะเคสที่เลือก และ scripts/cypress-runner.js สำหรับคำสั่ง
เพิ่มเคสด้วย scenario('ชื่อเคส', function ชื่อเคส() { ... }) ชื่อห้ามซ้ำ
เพิ่มคำสั่งสั้นใน package.json เมื่อต้องการ npm run ชื่อเคส โดยคำสั่งกลางค้นพบเคสใหม่ได้เอง
