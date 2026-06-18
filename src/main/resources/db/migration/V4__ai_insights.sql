-- =============================================
-- MindBridge V4 — AI Insights & Recommendations
-- =============================================

-- Stores AI-generated insight summaries (hero card + historical timeline)
CREATE TABLE IF NOT EXISTS ai_insights (
                                           id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    generated_date  DATE NOT NULL DEFAULT CURRENT_DATE,
    headline        TEXT NOT NULL,
    narrative       TEXT NOT NULL,
    wellness_score  INTEGER CHECK (wellness_score BETWEEN 0 AND 100),
    trend_vs_last   INTEGER,                          -- e.g. +4 or -2 vs last week
    trend_direction VARCHAR(20),                      -- IMPROVING | STABLE | DECLINING
    severity        VARCHAR(20) DEFAULT 'LOW',        -- LOW | MEDIUM | HIGH | CRITICAL
    raw_ai_response TEXT,                             -- full Gemini response for audit
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_insight_per_day UNIQUE (company_id, generated_date)
    );

-- Stores AI-generated action recommendations
CREATE TABLE IF NOT EXISTS ai_recommendations (
                                                  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    insight_id      UUID REFERENCES ai_insights(id) ON DELETE CASCADE,
    priority        VARCHAR(20) NOT NULL,             -- URGENT | HIGH | MEDIUM
    icon            VARCHAR(10),                      -- emoji icon
    title           VARCHAR(255) NOT NULL,
    detail          TEXT NOT NULL,
    action_label    VARCHAR(100) DEFAULT 'Take action',
    status          VARCHAR(20) DEFAULT 'PENDING',    -- PENDING | ACTIONED | DISMISSED
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ai_insights_company_date
    ON ai_insights (company_id, generated_date DESC);

CREATE INDEX IF NOT EXISTS idx_ai_recommendations_insight
    ON ai_recommendations (insight_id);

CREATE INDEX IF NOT EXISTS idx_ai_recommendations_company
    ON ai_recommendations (company_id, status);