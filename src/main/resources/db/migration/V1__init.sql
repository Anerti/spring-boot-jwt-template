CREATE TYPE IF NOT EXISTS user_role AS ENUM ('admin', 'customer');

CREATE TABLE IF NOT EXISTS "user" (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    verification_code VARCHAR(6),
    verified   BOOLEAN     NOT NULL DEFAULT false,
    role       user_role   NOT NULL DEFAULT 'customer',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
