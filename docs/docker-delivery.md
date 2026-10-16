# Eventsss classroom Docker delivery

ชุดนี้เปิด frontend, API, PostgreSQL และ MinIO จาก Docker images โดยไม่ต้องมี source code,
Java หรือ Node.js ใช้ Docker Desktop พร้อม Docker Compose v2 และอินเทอร์เน็ตสำหรับดึง image

## สถานะก่อนส่งมอบ

ก่อนให้อาจารย์ใช้ ต้องให้ release CI ผ่านและเผยแพร่ application images ทั้ง 3 ตัวบน GHCR
เป็น public ก่อน การเตรียมไฟล์หรือทดสอบ image ในเครื่องยังไม่ถือว่าเผยแพร่แล้ว
`versions.txt` ระบุ image tag และ source revision ของ backend/frontend แยกกัน

ไฟล์ที่ต้องอยู่ในโฟลเดอร์เดียวกัน:

- `docker-compose.yml`: กำหนด 4 services, network, health checks และพื้นที่เก็บข้อมูล
- `.env`: ค่าตั้งต้นพร้อม database/JWT/MinIO secrets ที่สุ่มเฉพาะชุดนี้ และ `IMAGE_TAG`
- `versions.txt`: เวอร์ชันของชุดส่งมอบ
- `README.md`: คู่มือนี้

เก็บ `.env` เป็นส่วนตัว และใช้ไฟล์เดิมเมื่อเปิดชุดเดิมครั้งถัดไป

## เปิดระบบหลังเผยแพร่ image แล้ว

เปิด Docker Desktop แล้วเปิด terminal ในโฟลเดอร์ที่แตกไฟล์ รันคำสั่งเดียว:

```powershell
docker compose up -d
```

รอให้ดาวน์โหลด image และเริ่ม services จากนั้นตรวจสถานะ:

```powershell
docker compose ps
docker compose logs --tail=100 frontend api
```

เปิดหน้าเว็บ `http://localhost:5173` เมื่อ services แสดง `healthy`
หากพอร์ตถูกใช้อยู่ ปรับ `FRONTEND_PORT` ใน `.env` พร้อมเพิ่ม URL ที่ใช้จริงใน
`CORS_ALLOWED_ORIGINS` และปรับ `FRONTEND_BASE_URL` ให้ตรงกัน แล้วรัน `up -d` อีกครั้ง

Images ของ API, frontend และ MinIO ใช้ `IMAGE_TAG` เดียวกันเพื่อให้เวอร์ชันตรงกัน
สำหรับ release จาก CI ค่า tag ประกอบด้วย backend commit SHA, workflow run ID และ attempt
เช่น `<SHA>-<run ID>-<attempt>` การรัน CI ซ้ำจึงไม่เขียนทับ tag ของชุดก่อนหน้า
คง `IMAGE_TAG` ที่มากับชุดนี้ไว้เมื่อต้องการใช้เวอร์ชันเดิม ส่วน `latest` เปลี่ยนตาม release
ที่ผ่านการตรวจล่าสุด PostgreSQL ใช้เวอร์ชันที่ระบุใน Compose ไม่ต้องติดตั้งฐานข้อมูลแยก

## ข้อมูลเริ่มต้นและการสมัคร

ฐานข้อมูลใหม่ไม่มี account หรือกิจกรรม Flyway สร้าง schema และ master roles ให้อัตโนมัติ
สมัครที่ `http://localhost:5173/register` แล้ว login ได้ทันทีใน classroom `local` profile
บัญชีที่สมัครจะได้สิทธิ์ `USER` ไม่มี username/password กลางที่ติดมากับ image

ระบบชำระเงินใช้ QR simulation สำหรับงานในชั้นเรียน ส่วนการส่งอีเมล reset password จริง
ต้องใส่ SMTP ของผู้ดูแลใน `.env` ก่อน (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`,
`MAIL_FROM_EMAIL`) แล้วรัน `docker compose up -d --no-deps api`
การสมัครและ login ใน local profile ไม่ต้องมี SMTP

## สร้างผู้ดูแลคนแรกครั้งเดียว

ขั้นตอนนี้ให้ผู้ดูแล installation ทำกับฐานข้อมูลใหม่ หลังสมัครบัญชีที่จะใช้ดูแลผ่านหน้าเว็บแล้ว
เปลี่ยน `teacher@example.com` ด้านล่างให้ตรงกับ email ที่สมัคร คำสั่งจะยอมทำงานเมื่อ account
นั้นเป็น `ACTIVE` และยังไม่มี super administrator ที่ active อยู่

รันทั้ง block ใน Windows PowerShell จากโฟลเดอร์ส่งมอบ:

```powershell
@'
BEGIN;
DO $setup$
DECLARE
    account_email text := 'teacher@example.com';
    account_id uuid;
BEGIN
    SELECT user_id INTO account_id
    FROM public.auth_users
    WHERE email_normalized = lower(btrim(account_email))
      AND status = 'ACTIVE';

    IF account_id IS NULL THEN
        RAISE EXCEPTION 'Register the chosen account first; it must be ACTIVE.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.auth_user_roles ur
        JOIN public.auth_users u ON u.user_id = ur.user_id
        JOIN public.auth_roles r ON r.role_id = ur.role_id
        WHERE u.status = 'ACTIVE'
          AND r.status = 'ACTIVE'
          AND r.role_code = 'SUPER_ADMIN'
    ) THEN
        RAISE EXCEPTION 'An active super administrator already exists; use the Users screen.';
    END IF;

    IF (SELECT count(*) FROM public.auth_roles
        WHERE role_code IN ('ADMIN', 'SUPER_ADMIN') AND status = 'ACTIVE') <> 2 THEN
        RAISE EXCEPTION 'Required roles are missing; check Flyway migrations.';
    END IF;

    INSERT INTO public.auth_user_roles (user_id, role_id, assigned_by_user_id)
    SELECT account_id, role_id, account_id
    FROM public.auth_roles
    WHERE role_code IN ('ADMIN', 'SUPER_ADMIN') AND status = 'ACTIVE'
    ON CONFLICT (user_id, role_id) DO NOTHING;
END;
$setup$;
COMMIT;
'@ | docker compose exec -T postgres sh -c 'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --set=ON_ERROR_STOP=1'

if ($LASTEXITCODE -ne 0) { throw 'Administrator setup failed; read the SQL error above.' }
```

จากนั้น logout แล้ว login ใหม่ ระบบจะแสดงหน้า Select role สำหรับบัญชีที่มีหลาย role
เลือก `ADMIN` เพื่อเข้าหน้าผู้จัดกิจกรรม หรือเลือก `SUPER_ADMIN` เพื่อเข้าหน้าผู้ดูแลระบบ
เมื่อเลือก `SUPER_ADMIN` แล้ว ใช้หน้า Users เพื่อกำหนด role ให้บัญชีอื่นในภายหลังได้
ถ้าต้องการบัญชี ADMIN แยก ให้สมัครอีกบัญชีแล้วกำหนด `ADMIN` ผ่านหน้า Users
ไม่ต้องรัน SQL นี้ซ้ำ

บน macOS/Linux ใช้ SQL เดียวกันระหว่าง `BEGIN;` ถึง `COMMIT;` ส่งเข้า `psql` ผ่าน:

```bash
docker compose exec -T postgres sh -c 'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --set=ON_ERROR_STOP=1' <<'SQL'
-- Paste the SQL block here, replacing this comment.
SQL
```

## หยุดและเปิดใหม่โดยเก็บข้อมูล

```powershell
docker compose down
docker compose up -d
```

ข้อมูลบัญชี/กิจกรรมอยู่ใน PostgreSQL volume และรูปอยู่ใน MinIO volume การ recreate container
ไม่ลบข้อมูลเหล่านี้ คง `.env`, project name และ volume names เดิม ห้ามใช้ `down -v` เมื่อต้องการ
เก็บข้อมูล และอย่าแทนที่ secrets ของ installation เดิมด้วย `.env` จากชุดส่งมอบใหม่

## ทดสอบ preview ในเครื่องก่อนเผยแพร่

ผู้เตรียมงานต้อง build และ tag candidate images ให้ตรงกับ `IMAGE_TAG` ใน `.env` ก่อน แล้วใช้:

```powershell
docker compose up -d --pull never
```

ตัวเลือกนี้ใช้ image ที่มีอยู่ในเครื่อง เหมาะสำหรับทดสอบชุดส่งมอบก่อนเผยแพร่เท่านั้น
ก่อนส่งให้อาจารย์ต้องตรวจว่าดึง images ทั้งหมดแบบ anonymous ได้จริง

ถ้าขึ้น `denied` หรือ `unauthorized` ในโหมดปกติ ให้ผู้เตรียมงานตรวจ image tag และ package
visibility บน GHCR หากเว็บขึ้นแต่ API มีปัญหา ดู `docker compose logs --tail=200 api frontend`
และตรวจว่า `http://localhost:5173/api/health` ตอบ `OK` แทนหน้า HTML
