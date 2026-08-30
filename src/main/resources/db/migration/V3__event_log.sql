DO $$ BEGIN
    CREATE TYPE jwt_template_app.event_log_status AS ENUM ('INFO', 'APPROVED', 'SECURITY', 'WARNING');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS jwt_template_app.event_log (
        id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        host_id    UUID        NOT NULL,
        status     jwt_template_app.event_log_status NOT NULL DEFAULT 'INFO',
        description VARCHAR(100),
        created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
        CONSTRAINT fk_host_id FOREIGN KEY (host_id)
        REFERENCES jwt_template_app.host(id)
        ON DELETE CASCADE
    );
