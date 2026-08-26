CREATE TABLE IF NOT EXISTS jwt_template_app.event_log (
        id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        host_id    UUID        NOT NULL,
        description VARCHAR(100),
        created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
        CONSTRAINT fk_host_id FOREIGN KEY (host_id)
        REFERENCES jwt_template_app.host(id)
        ON DELETE CASCADE
    );


