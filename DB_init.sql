-- 1. USERS
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

-- 2. AUDIT_LOGS
CREATE TABLE audit_logs (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(255) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id INT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. EVENTS
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

-- 4. EVENT_ADMINS
CREATE TABLE event_admins (
    event_id INT REFERENCES events(id) ON DELETE CASCADE,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, user_id)
);

-- 5. EVENT_APPROVALS
CREATE TABLE event_approvals (
    id SERIAL PRIMARY KEY,
    event_id INT REFERENCES events(id) ON DELETE CASCADE,
    reviewer_id INT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL,
    comment TEXT,
    reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. CHANGE_REQUESTS
CREATE TABLE change_requests (
    id SERIAL PRIMARY KEY,
    event_id INT REFERENCES events(id) ON DELETE CASCADE,
    submitted_by INT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(50) DEFAULT 'pending',
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

-- 7. CHANGE_REQUEST_ITEMS
CREATE TABLE change_request_items (
    id SERIAL PRIMARY KEY,
    change_request_id INT REFERENCES change_requests(id) ON DELETE CASCADE,
    field_name VARCHAR(255) NOT NULL,
    old_value TEXT,
    new_value TEXT
);

-- 8. REGISTRATIONS
CREATE TABLE registrations (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    event_id INT REFERENCES events(id) ON DELETE CASCADE,
    status VARCHAR(50) DEFAULT 'registered',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP
);

-- 9. PAYMENTS
CREATE TABLE payments (
    id SERIAL PRIMARY KEY,
    registration_id INT REFERENCES registrations(id) ON DELETE CASCADE,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(100),
    payment_status VARCHAR(50) DEFAULT 'pending',
    transaction_reference VARCHAR(255),
    paid_at TIMESTAMP
);