DO $$ BEGIN
    CREATE TYPE jwt_template_app.host_status AS ENUM ('ACTIVE', 'INACTIVE', 'BANNED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS jwt_template_app.host (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
    user_id     UUID        NOT NULL,
    ip_address  VARCHAR(255) NOT NULL,
    status      jwt_template_app.host_status NOT NULL DEFAULT 'INACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT fk_user_id FOREIGN KEY (user_id)
    REFERENCES jwt_template_app."user"(id)
    ON DELETE CASCADE
);
