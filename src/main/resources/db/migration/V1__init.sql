-- =============================================
-- MindBridge Database Schema — V1 Initial
-- =============================================

-- Companies (Multi-tenant root)
CREATE TABLE IF NOT EXISTS companies (
                                         id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    plan        VARCHAR(50)  NOT NULL DEFAULT 'starter',  -- starter | growth | enterprise
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- HR Manager / Admin users
CREATE TABLE IF NOT EXISTS users (
                                     id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,       -- BCrypt hashed
    full_name    VARCHAR(255),
    role         VARCHAR(50)  NOT NULL DEFAULT 'HR_MANAGER',  -- HR_MANAGER | ADMIN
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Teams within a company
CREATE TABLE IF NOT EXISTS teams (
                                     id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Employees (the ones doing daily check-ins)
CREATE TABLE IF NOT EXISTS employees (
                                         id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    team_id         UUID REFERENCES teams(id),
    slack_user_id   VARCHAR(100),             -- Slack user ID for bot integration
    email           VARCHAR(255),
    full_name       VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Daily Check-ins (Core data — captured by Check-in Agent)
CREATE TABLE IF NOT EXISTS check_ins (
                                         id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    company_id          UUID NOT NULL REFERENCES companies(id),   -- Denormalized for RLS
    check_in_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    energy_score        INTEGER CHECK (energy_score BETWEEN 1 AND 10),
    workload_tag        VARCHAR(100),          -- e.g. "overwhelming", "manageable"
    team_support_score  INTEGER CHECK (team_support_score BETWEEN 1 AND 10),
    raw_sentiment       TEXT,                  -- AI-extracted sentiment summary
    flagged_keywords    TEXT[],                -- e.g. ARRAY['deadline', 'exhausted']
    ai_conversation     TEXT,                  -- Full conversation JSON (for audit)
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    -- One check-in per employee per day
    CONSTRAINT unique_checkin_per_day UNIQUE (employee_id, check_in_date)
    );

-- Burnout Scores (Computed nightly by Pattern Agent)
CREATE TABLE IF NOT EXISTS burnout_scores (
                                              id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    company_id          UUID NOT NULL REFERENCES companies(id),
    score_date          DATE NOT NULL DEFAULT CURRENT_DATE,
    risk_level          VARCHAR(20) NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL
    risk_score          INTEGER CHECK (risk_score BETWEEN 0 AND 100),
    trend_direction     VARCHAR(20),            -- IMPROVING | STABLE | DECLINING
    primary_stressors   TEXT[],
    ai_reasoning        TEXT,                   -- AI explanation of the score
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_score_per_day UNIQUE (employee_id, score_date)
    );

-- Interventions Log (What action was taken and when)
CREATE TABLE IF NOT EXISTS interventions (
                                             id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    company_id          UUID NOT NULL REFERENCES companies(id),
    burnout_score_id    UUID REFERENCES burnout_scores(id),
    intervention_type   VARCHAR(50),            -- NUDGE | TIP | HR_ALERT | CRITICAL_ALERT
    message_sent        TEXT,
    hr_notified         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- =============================================
-- Indexes for Performance
-- =============================================
CREATE INDEX idx_check_ins_employee_date     ON check_ins (employee_id, check_in_date DESC);
CREATE INDEX idx_check_ins_company           ON check_ins (company_id);
CREATE INDEX idx_burnout_scores_employee     ON burnout_scores (employee_id, score_date DESC);
CREATE INDEX idx_burnout_scores_company      ON burnout_scores (company_id);
CREATE INDEX idx_employees_company           ON employees (company_id);
CREATE INDEX idx_employees_slack             ON employees (slack_user_id);

-- =============================================
-- Seed Data (for local development)
-- =============================================
INSERT INTO companies (id, name, plan) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Demo Corp', 'growth');

INSERT INTO teams (id, company_id, name) VALUES
                                             ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'Engineering'),
                                             ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', 'Product');

-- Password: 'password123' (BCrypt hashed — change before real use)
INSERT INTO users (company_id, email, password, full_name, role) VALUES
    ('a0000000-0000-0000-0000-000000000001',
     'hr@democorp.com',
     '$2a$10$N.zmdr9zkoa05SoD2tPtguudgIV5HoME3HjVGPKHJY2FgPWOlHaYe',
     'HR Manager',
     'HR_MANAGER');

INSERT INTO employees (company_id, team_id, email, full_name, slack_user_id) VALUES
                                                                                 ('a0000000-0000-0000-0000-000000000001',
                                                                                  'b0000000-0000-0000-0000-000000000001',
                                                                                  'alice@democorp.com', 'Alice Johnson', 'U_ALICE_001'),
                                                                                 ('a0000000-0000-0000-0000-000000000001',
                                                                                  'b0000000-0000-0000-0000-000000000001',
                                                                                  'bob@democorp.com', 'Bob Smith', 'U_BOB_002');