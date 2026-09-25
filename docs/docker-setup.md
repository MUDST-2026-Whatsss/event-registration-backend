# Docker setup, database backup, and restore

คู่มือนี้ใช้สำหรับเริ่ม backend หลัง clone repository ใหม่ รวมถึงสำรองฐานข้อมูลจาก
managed PostgreSQL และนำข้อมูลกลับมาใช้กับ PostgreSQL ใน Docker เครื่อง local

## ติดตั้งระบบพร้อม database dump

ส่วนนี้เป็น flow หลักสำหรับติดตั้งระบบจาก source code และไฟล์ database dump ทำตามจากบนลงล่าง
จนเปิดหน้าเว็บและ login ได้

### ไฟล์และข้อมูลที่ใช้

| รายการ | จำเป็น | หมายเหตุ |
| --- | --- | --- |
| Backend source | จำเป็น | ใช้ branch/commit ที่ต้องการทดสอบ |
| Frontend source | จำเป็นเมื่อทดสอบหน้าเว็บ | ใช้ version ที่เข้ากับ backend |
| PostgreSQL dump | จำเป็นเมื่อใช้ข้อมูลเดิม | ไฟล์ `.dump` ห้ามใส่ Git |
| SHA-256 ของ dump | แนะนำ | ใช้ยืนยันว่าไฟล์ไม่เสียหรือถูกเปลี่ยน |
| Test account | แนะนำ | ห้ามเขียน password ใน README หรือ source code |
| MinIO bucket backup | เฉพาะเมื่อต้องใช้รูปเดิม | Database dump ไม่มีไฟล์รูป |

ไม่ควรนำ `.env.server` มาใช้กับ local เพราะมี credential ที่เข้าถึงฐานข้อมูลจริง ให้สร้าง
`.env.local` จาก template และใช้ secret คนละชุดกับ server

### A. Clone frontend และ backend

```bash
mkdir event-registration-workspace
cd event-registration-workspace

git clone https://github.com/MUDST-2026-Whatsss/event-registration-backend.git
git clone https://github.com/MUDST-2026-Whatsss/event-registration-frontend.git
```

หาก repository เป็น private ต้อง login GitHub ด้วย account ที่มีสิทธิ์ก่อน

### B. วางและตรวจ database dump

นำไฟล์ dump ไปวางใน `event-registration-backend/backups/` จากนั้น:

```bash
cd event-registration-backend
mkdir -p backups
chmod 700 backups
chmod 600 backups/event-registration-server-20260925-210801.dump

shasum -a 256 backups/event-registration-server-20260925-210801.dump
# ถ้ามี checksum file:
cd backups
shasum -a 256 -c event-registration-server-20260925-210801.dump.sha256
cd ..
```

ชื่อไฟล์เป็นตัวอย่าง หากใช้ชื่ออื่นให้เปลี่ยนทุกคำสั่งให้ตรงกัน ค่า SHA-256 ต้องตรงกับค่าที่บันทึก
ไว้ตอนสร้าง dump หากไม่ตรง ห้าม restore เพราะไฟล์อาจเสียหรือถูกเปลี่ยน

ตรวจว่าเป็น PostgreSQL archive ที่อ่านได้:

```bash
docker run --rm \
  --volume "$PWD/backups:/backups:ro" \
  postgres:18.6 \
  pg_restore --list "/backups/event-registration-server-20260925-210801.dump" >/dev/null
```

คำสั่งที่สำเร็จจะไม่แสดง error และมี exit code `0`

### C. สร้าง local environment

```bash
cp .env.example .env.local
chmod 600 .env.local

openssl rand -base64 36
openssl rand -base64 48
```

นำค่าที่สร้างไปแทน placeholder ใน `.env.local`:

- ใช้ค่าสุ่มชุดหนึ่งเป็น `POSTGRES_PASSWORD`
- ใช้ค่าสุ่มอย่างน้อย 48 bytes เป็น `JWT_SECRET`
- กำหนด `STORAGE_ACCESS_KEY` สำหรับ MinIO local
- ใช้ค่าสุ่มอีกชุดเป็น `STORAGE_SECRET_KEY`
- คง `SPRING_PROFILES_ACTIVE=local`
- คง `COOKIE_SECURE=false` สำหรับ `http://localhost`
- คง `CORS_ALLOWED_ORIGINS=http://localhost:5173`

ห้ามใส่ `DB_URL`, `DB_USERNAME` หรือ `DB_PASSWORD` ของ server ใน `.env.local`

ตรวจ configuration ก่อนสร้าง container:

```bash
docker compose --env-file .env.local config --quiet
```

### D. เปิด PostgreSQL และ MinIO local

```bash
docker compose --env-file .env.local up -d --build postgres minio
docker compose --env-file .env.local ps

set -a
source .env.local
set +a

docker compose --env-file .env.local exec -T postgres \
  pg_isready --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"
```

รอจน PostgreSQL แสดง `healthy` และ `accepting connections` ก่อนทำขั้นตอนต่อไป

### E. Restore dump ลง local PostgreSQL

คำสั่งต่อไปนี้ลบเฉพาะ database ใน Docker local แล้วแทนด้วยข้อมูลจาก dump ห้ามเปลี่ยนไปใช้
`.env.server`

```bash
BACKUP_FILE="event-registration-server-20260925-210801.dump"

docker compose --env-file .env.local stop api

docker compose --env-file .env.local exec -T postgres \
  dropdb --if-exists --force --username="$POSTGRES_USER" "$POSTGRES_DB"

docker compose --env-file .env.local exec -T postgres \
  createdb --username="$POSTGRES_USER" "$POSTGRES_DB"

docker compose --env-file .env.local exec -T postgres \
  pg_restore \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    --no-owner \
    --no-privileges \
    --exit-on-error \
  < "backups/$BACKUP_FILE"
```

ถ้า `pg_restore` จบโดยไม่มี error จึงถือว่า restore สำเร็จ

### F. เปิด API และตรวจ database

```bash
docker compose --env-file .env.local up -d api
docker compose --env-file .env.local logs -f api
```

เมื่อเห็น log ว่า application started ให้กด `Ctrl+C`; container จะยังทำงานอยู่ แล้วตรวจ:

```bash
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8080/actuator/health

docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT role_code FROM auth_roles ORDER BY role_code;"
```

Migration ทุกแถวต้องเป็น `success = true` และต้องพบ `USER`, `ADMIN`, `SUPER_ADMIN`

### G. เปิด frontend

เปิด terminal ใหม่:

```bash
cd event-registration-workspace/event-registration-frontend
cp .env.example .env
npm ci
npm run dev
```

หาก terminal อยู่ใน backend ให้ใช้ `cd ../event-registration-frontend` แทน เปิด
`http://localhost:5173` แล้วทดสอบ register/login Frontend จะ proxy `/api` ไป backend ที่
`http://localhost:8080`

User ที่อยู่ใน dump ใช้ password เดิม หากไม่มี account ที่ทราบ password สามารถสมัครใหม่ผ่าน
`/register`; local profile จะ activate account ใหม่ให้อัตโนมัติ

### H. Checklist ระบบพร้อมใช้งาน

- `docker compose --env-file .env.local ps` แสดง PostgreSQL, MinIO และ API ทำงานอยู่
- PostgreSQL เป็น `healthy`
- API health ทั้งสอง URL ตอบสำเร็จ
- Flyway history ไม่มี migration ที่ล้มเหลว
- หน้า `http://localhost:5173` เปิดได้
- สมัคร user ใหม่หรือ login ด้วย test account ได้
- จำนวนข้อมูลใน `auth_users` และ `participants` เพิ่ม/อ่านได้ตามที่คาด

ถ้าต้องใช้รูปเดิม ต้อง restore MinIO bucket backup เพิ่มด้วย การ restore database อย่างเดียวทำให้
event metadata อยู่ครบ แต่ URL รูปเดิมอาจตอบ `404` เพราะ object ไม่ได้อยู่ใน MinIO local

## 1. สิ่งที่ต้องติดตั้ง

- Git
- Docker Desktop พร้อม Docker Compose v2
- Node.js `^22.18.0` หรือ `>=24.12.0` และ npm สำหรับ frontend
- พอร์ตว่างสำหรับ API, PostgreSQL และ MinIO (ค่าเริ่มต้น `8080`, `5432`, `9000`, `9001`)
- Internet สำหรับดาวน์โหลด image/dependency ในการ build ครั้งแรก

ตรวจสอบเครื่องมือ:

```bash
docker version
docker compose version
node --version
npm --version
```

## 2. เตรียม environment หลัง clone

```bash
git clone https://github.com/MUDST-2026-Whatsss/event-registration-backend.git
cd event-registration-backend
cp .env.example .env.local
```

แก้ placeholder ทุกค่าใน `.env.local` โดยเฉพาะ:

- `POSTGRES_PASSWORD`
- `JWT_SECRET` ซึ่งสร้างได้ด้วย `openssl rand -base64 48`
- `STORAGE_ACCESS_KEY`
- `STORAGE_SECRET_KEY`

`.env.local`, `.env.server`, `.env` และโฟลเดอร์ `backups/` ถูก ignore จาก Git
ห้าม commit credential หรือ database dump ขึ้น repository

แนะนำให้แยก environment ดังนี้:

| ไฟล์ | ใช้กับ | Compose file |
| --- | --- | --- |
| `.env.local` | PostgreSQL + MinIO + API ใน Docker เครื่อง local | `docker-compose.yml` |
| `.env.server` | API/MinIO ในเครื่อง local แต่ต่อ managed PostgreSQL | `docker-compose.server.yml` |

`.env.server` ต้องมี `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA` และค่าระบบอื่นจาก
`.env.example` แต่ไม่ต้องมี `POSTGRES_*` เพราะ Compose ชุดนี้ไม่สร้าง PostgreSQL local

## 3. ตั้งแต่ clone จนใช้งานระบบ local ได้

ลำดับที่แนะนำคือเริ่ม infrastructure ก่อน ตรวจ PostgreSQL แล้วจึงเริ่ม API วิธีนี้ทำให้แยกได้
ทันทีว่าปัญหาเกิดจาก database, migration หรือ application

### 3.1 ตรวจ environment

ตรวจสอบ Compose configuration ก่อนเริ่ม:

```bash
docker compose --env-file .env.local config --quiet
```

คำสั่งต้องจบโดยไม่มี error หากพบข้อความ `required variable ... is missing` ให้เติมค่านั้นใน
`.env.local` ก่อน อย่าใช้ `.env.server` กับ local Compose

### 3.2 เริ่ม PostgreSQL และ MinIO

```bash
docker compose --env-file .env.local up -d --build postgres minio
docker compose --env-file .env.local ps
```

รอจน service `postgres` แสดงสถานะ `healthy` จากนั้นทดสอบการเชื่อมต่อ:

```bash
set -a
source .env.local
set +a

docker compose --env-file .env.local exec -T postgres \
  pg_isready --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"
```

ผลที่พร้อมใช้งานต้องลงท้ายด้วย `accepting connections`

### 3.3 เลือกข้อมูลเริ่มต้น

เลือกเพียงหนึ่งทาง:

1. **ฐานข้อมูลใหม่:** ไปขั้นตอน 3.4 ได้เลย เมื่อ API เริ่ม Flyway จะสร้าง schema และ master data
   ที่อยู่ใน migration ให้ทั้งหมด
2. **ใช้ข้อมูลจาก server dump:** ทำขั้นตอน [Restore dump เข้า PostgreSQL local](#6-restore-dump-เข้า-postgresql-local)
   ก่อน โดยขั้นตอน restore จะลบเฉพาะ database local แล้วเปิด API ให้เมื่อเสร็จ

ไม่ต้องรัน `DB_init.sql` หรือ `DB_proposal_v2.sql` เพราะเป็น schema รุ่นเก่าที่ไม่ตรงกับระบบ
UUID/Flyway ปัจจุบัน

### 3.4 เริ่ม API และให้ Flyway เตรียม schema

```bash
docker compose --env-file .env.local up -d api
docker compose --env-file .env.local ps
docker compose --env-file .env.local logs -f api
```

Flyway จะสร้าง/อัปเดต schema อัตโนมัติเมื่อ API เริ่มทำงาน ส่วน Hibernate จะตรวจ schema
ด้วย `ddl-auto=validate` และจะไม่แก้ table เอง เมื่อ log แสดงว่า application started แล้ว ให้กด
`Ctrl+C` เพื่อออกจากการติดตาม log โดย container จะยังทำงานต่อ

### 3.5 ตรวจ migration, table และ master role

```bash
docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT count(*) AS application_tables FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';"

docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT role_code FROM auth_roles ORDER BY role_code;"
```

ทุกแถวใน `flyway_schema_history` ต้องเป็น `success = true` และ role ขั้นต่ำควรมี `USER`,
`ADMIN`, `SUPER_ADMIN` หาก API หยุดเพราะ Flyway หรือ Hibernate validation ห้ามแก้ด้วย
`ddl-auto=create/update`; ให้ตรวจ migration และ database ที่เลือกอยู่

### 3.6 ตรวจ API และ MinIO

จุดตรวจสอบหลังระบบพร้อม:

- API health: `http://localhost:8080/api/health`
- Actuator health: `http://localhost:8080/actuator/health`
- MinIO S3 API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`

```bash
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8080/actuator/health
```

MinIO bucket จะถูกสร้างแบบ private โดย API เมื่อมีการ upload รูปครั้งแรก จึงเป็นปกติที่หน้า
Console ยังไม่มี bucket ทันทีหลังเริ่มระบบ

### 3.7 เปิด frontend และสร้าง user แรก

เปิด terminal ใหม่ โดยสมมติว่า frontend อยู่ข้าง backend ในโฟลเดอร์เดียวกัน:

```bash
cd ../event-registration-frontend
cp .env.example .env
npm ci
npm run dev
```

เปิด `http://localhost:5173/register` แล้วสมัคร user จากหน้าเว็บ จากนั้น login ได้ทันที เพราะ
`SPRING_PROFILES_ACTIVE=local` เปิด auto-verification เฉพาะการพัฒนา Frontend ส่ง `/api`
ผ่าน Vite proxy ไป `http://localhost:8080` จึงไม่ต้องใส่ token หรือ cookie ลง JavaScript เอง

ตรวจว่าข้อมูลถูกบันทึกจริงโดยไม่แสดง password hash:

```bash
cd ../event-registration-backend
set -a
source .env.local
set +a

docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT count(*) AS users FROM auth_users; SELECT count(*) AS participants FROM participants;"
```

ชื่อโฟลเดอร์ backend อาจต่างจากตัวอย่างหากกำหนดชื่อปลายทางเองตอน `git clone`

### 3.8 คำสั่งใช้งานประจำ

เริ่มระบบครั้งถัดไป:

```bash
docker compose --env-file .env.local up -d
```

ดู log หรือหยุดระบบ:

```bash
docker compose --env-file .env.local logs --tail=200 api postgres minio
docker compose --env-file .env.local down
```

`docker compose down` ไม่ลบข้อมูลใน named volumes แต่ `docker compose down -v` จะลบทั้ง
PostgreSQL และ MinIO local อย่างถาวร จึงต้องมี backup ที่ตรวจสอบแล้วก่อนใช้ `-v`

## 4. รัน API local โดยต่อฐานข้อมูลบน server

ใช้โหมดนี้เฉพาะเมื่อตั้งใจทดสอบกับฐานข้อมูลร่วม ห้ามรัน integration test, reset script หรือ
คำสั่งแก้ข้อมูลทดลองกับ production/shared database

```bash
docker compose --env-file .env.server \
  -f docker-compose.server.yml config --quiet

docker compose --env-file .env.server \
  -f docker-compose.server.yml up -d --build minio api

docker compose --env-file .env.server \
  -f docker-compose.server.yml logs -f api
```

## 5. สำรอง managed PostgreSQL ลงเครื่อง

Database dump มีข้อมูล account, participant, session metadata และข้อมูลส่วนบุคคล ให้เก็บเฉพาะ
ในเครื่องที่ได้รับอนุญาต จำกัดสิทธิ์ไฟล์ และลบเมื่อหมดความจำเป็น

คำสั่งนี้ใช้ PostgreSQL 18 client ใน Docker เพื่อไม่ให้ติดปัญหา `pg_dump` รุ่นเก่ากว่า server:

```bash
set -a
source .env.server
set +a

mkdir -p backups
chmod 700 backups
BACKUP_FILE="event-registration-$(date +%Y%m%d-%H%M%S).dump"

docker run --rm \
  --env PGPASSWORD="$DB_PASSWORD" \
  --volume "$PWD/backups:/backups" \
  postgres:18.6 \
  pg_dump \
    --dbname="${DB_URL#jdbc:}" \
    --username="$DB_USERNAME" \
    --format=custom \
    --compress=9 \
    --no-owner \
    --no-privileges \
    --file="/backups/$BACKUP_FILE"

chmod 600 "backups/$BACKUP_FILE"
```

ตรวจว่า archive อ่านได้ก่อนถือว่า backup สำเร็จ:

```bash
docker run --rm \
  --volume "$PWD/backups:/backups:ro" \
  postgres:18.6 \
  pg_restore --list "/backups/$BACKUP_FILE" >/dev/null

ls -lh "backups/$BACKUP_FILE"
```

`pg_dump` เป็น consistent logical backup และไม่ต้องหยุด API แต่ข้อมูลที่ commit หลัง snapshot
เริ่มต้นจะไม่อยู่ในไฟล์นี้ หากต้องการ backup ที่ผูกกับเวลาธุรกรรมสำคัญให้หยุดการเขียนข้อมูลก่อน

## 6. Restore dump เข้า PostgreSQL local

ขั้นตอนนี้จะลบ database local ปลายทางก่อน restore และไม่กระทบ managed PostgreSQL บน server
ตรวจให้แน่ใจว่าโหลด `.env.local` ไม่ใช่ `.env.server`

```bash
set -a
source .env.local
set +a

BACKUP_FILE="event-registration-server-20260925-210801.dump"

docker compose --env-file .env.local up -d postgres minio
docker compose --env-file .env.local stop api

docker compose --env-file .env.local exec -T postgres \
  dropdb --if-exists --force --username="$POSTGRES_USER" "$POSTGRES_DB"

docker compose --env-file .env.local exec -T postgres \
  createdb --username="$POSTGRES_USER" "$POSTGRES_DB"

docker compose --env-file .env.local exec -T postgres \
  pg_restore \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    --no-owner \
    --no-privileges \
    --exit-on-error \
  < "backups/$BACKUP_FILE"

docker compose --env-file .env.local up -d api
docker compose --env-file .env.local logs -f api
```

ตรวจ migration และจำนวน table หลัง restore:

```bash
docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

docker compose --env-file .env.local exec -T postgres \
  psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --command="SELECT count(*) AS application_tables FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';"
```

## 7. ข้อจำกัดของ database dump

PostgreSQL dump เก็บเฉพาะ schema และข้อมูลในฐานข้อมูล ไม่รวมไฟล์รูปใน MinIO โดยค่า
`events.image_url` เก็บเพียง object key เท่านั้น หากต้องการให้รูปจาก server แสดงใน local ต้อง
backup/restore MinIO bucket ที่สอดคล้องกันด้วย หรือยอมรับว่ารูปเดิมจะตอบกลับเป็น not found

อย่านำ production object storage credential มาใช้เป็น MinIO local และอย่าเปิดพอร์ต MinIO
Console ต่อสาธารณะ

## 8. Troubleshooting

### Compose แจ้งว่า environment variable ไม่มีค่า

ยืนยันว่าใช้ `--env-file` ถูกโหมดและไม่ได้ปล่อย placeholder ไว้:

```bash
docker compose --env-file .env.local config --quiet
```

### API ต่อ PostgreSQL ไม่ได้

ตรวจ health และ log:

```bash
docker compose --env-file .env.local ps
docker compose --env-file .env.local logs --tail=200 postgres api
```

ใน local Compose ค่า `DB_URL` ภายใน container ถูกกำหนดเป็น host `postgres` อัตโนมัติ ไม่ใช่
`localhost`

### API เริ่มไม่ได้หลัง restore

ตรวจ `flyway_schema_history` และ log ก่อน ห้ามแก้ migration ที่ apply ไปแล้วหรือเปิด
`ddl-auto=create/update` เพื่อบังคับ schema ให้ผ่าน ให้สร้าง migration ใหม่ตามลำดับแทน
