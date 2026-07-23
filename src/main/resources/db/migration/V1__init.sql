DO $$ BEGIN
    CREATE TYPE jwt_template_app.user_role AS ENUM ('ADMIN', 'CUSTOMER');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS jwt_template_app."user" (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(50) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    verification_code VARCHAR(6),
    verified   BOOLEAN     NOT NULL DEFAULT false,
    role       jwt_template_app.user_role NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
