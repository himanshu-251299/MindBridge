-- =============================================
-- MindBridge V3 — Employee Profile Enhancement
-- =============================================

-- Add department and role title to employees
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS department  VARCHAR(100);

-- Update seed employees with sample data
UPDATE employees SET department = 'Engineering'
WHERE email = 'alice@democorp.com';

UPDATE employees SET department = 'Product'
WHERE email = 'bob@democorp.com';