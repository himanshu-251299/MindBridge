-- =============================================
-- MindBridge V2 — Employee Self-Registration
-- =============================================

-- Add invite code to companies (HR shares this with employees to join)
ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS invite_code VARCHAR(12) UNIQUE;

-- Generate a random 8-char invite code for existing companies
UPDATE companies
SET invite_code = UPPER(SUBSTRING(MD5(RANDOM()::TEXT), 1, 8))
WHERE invite_code IS NULL;

-- Add password column to employees (for self-registration login)
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS password VARCHAR(255);

-- Add role to employees
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS role VARCHAR(50) DEFAULT 'EMPLOYEE';

-- Update seed employee with a dummy password ('password123' BCrypt hashed)
UPDATE employees
SET password = '$2a$10$N.zmdr9zkoa05SoD2tPtguudgIV5HoME3HjVGPKHJY2FgPWOlHaYe',
    role     = 'EMPLOYEE'
WHERE email IN ('alice@democorp.com', 'bob@democorp.com');

-- Show the invite code for Demo Corp (use this to test registration)
-- SELECT name, invite_code FROM companies WHERE name = 'Demo Corp';