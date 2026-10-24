-- Audit log for the "Ask your documents" chat (POST /api/search/chat).
-- Questions and answers are stored with high-risk identifiers already masked.
CREATE TABLE IF NOT EXISTS chat_logs (
    id                uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           uuid REFERENCES users(id) ON DELETE SET NULL,
    workspace_id      uuid REFERENCES workspaces(id) ON DELETE CASCADE,
    question          text NOT NULL,
    answer            text,
    outcome           varchar(30) NOT NULL,
    sources           varchar(255),
    passages_used     integer NOT NULL DEFAULT 0,
    sanitized_passages integer NOT NULL DEFAULT 0,
    redactions        integer NOT NULL DEFAULT 0,
    latency_ms        integer,
    created_at        timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_logs_workspace ON chat_logs (workspace_id, created_at DESC);
