-- =====================================================
CREATE OR REPLACE FUNCTION auth_register_user(
    p_email TEXT,
    p_password_hash TEXT,
    p_display_name TEXT,
    p_photo_url TEXT
) RETURNS UUID LANGUAGE plpgsql AS $$
DECLARE
    v_user_id UUID;
    v_token UUID;
BEGIN
    INSERT INTO users (email, password_hash, display_name, photo_url)
    VALUES (p_email, p_password_hash, p_display_name, p_photo_url)
    RETURNING id INTO v_user_id;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (v_user_id, 'register', jsonb_build_object('email', p_email));

    INSERT INTO email_verifications (user_id, expires_at)
    VALUES (v_user_id, now() + interval '24 hours')
    RETURNING token INTO v_token;

    RETURN v_token;
EXCEPTION WHEN unique_violation THEN
    -- map unique constraint to a readable error for callers
    RAISE EXCEPTION 'email_exists';
END;
$$;

CREATE OR REPLACE FUNCTION auth_verify_email(p_token UUID) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    v_user_id UUID;
BEGIN
    SELECT user_id INTO v_user_id
    FROM email_verifications
    WHERE token = p_token AND expires_at > now();

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    UPDATE users SET email_verified = TRUE WHERE id = v_user_id;
    DELETE FROM email_verifications WHERE token = p_token;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (v_user_id, 'email_verified', jsonb_build_object('token', p_token::text));

    RETURN TRUE;
END;
$$;


CREATE OR REPLACE FUNCTION auth_sign_in_basic(p_email TEXT, p_password_hash TEXT) RETURNS TABLE(
    session_id UUID,
    access_token TEXT,
    refresh_token UUID,
    access_expires_at TIMESTAMPTZ,
    refresh_expires_at TIMESTAMPTZ
) LANGUAGE plpgsql AS $$
DECLARE
    v_user users%ROWTYPE;
    v_access_window INTERVAL := interval '15 minutes';
    v_refresh_window INTERVAL := interval '30 days';
    v_refresh UUID := gen_random_uuid();
BEGIN
    SELECT * INTO v_user FROM users WHERE email = p_email;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'invalid_credentials';
    END IF;

    IF v_user.password_hash IS NULL OR v_user.password_hash <> p_password_hash THEN
        RAISE EXCEPTION 'invalid_credentials';
    END IF;

    IF NOT v_user.email_verified THEN
        RAISE EXCEPTION 'email_not_verified';
    END IF;

    INSERT INTO sessions (user_id, access_token, refresh_token, access_expires_at, refresh_expires_at)
    VALUES (v_user.id, encode(gen_random_bytes(32), 'hex'), v_refresh, now() + v_access_window, now() + v_refresh_window)
    RETURNING id, access_token, refresh_token, access_expires_at, refresh_expires_at
    INTO session_id, access_token, refresh_token, access_expires_at, refresh_expires_at;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (v_user.id, 'sign_in', jsonb_build_object('session', session_id::text));

    RETURN NEXT;
END;
$$;


CREATE OR REPLACE FUNCTION auth_refresh_session(p_refresh_token UUID) RETURNS TABLE(
    new_refresh_token UUID,
    new_access_token TEXT,
    new_access_expires TIMESTAMPTZ,
    new_refresh_expires TIMESTAMPTZ
) LANGUAGE plpgsql AS $$
DECLARE
    v_s sessions%ROWTYPE;
    v_new_refresh UUID := gen_random_uuid();
    v_access_window INTERVAL := interval '15 minutes';
    v_refresh_window INTERVAL := interval '30 days';
BEGIN
    SELECT * INTO v_s FROM sessions WHERE refresh_token = p_refresh_token AND revoked = FALSE AND refresh_expires_at > now();

    IF NOT FOUND THEN
        RAISE EXCEPTION 'invalid_refresh_token';
    END IF;

    UPDATE sessions SET revoked = TRUE WHERE id = v_s.id;

    INSERT INTO sessions (user_id, access_token, refresh_token, access_expires_at, refresh_expires_at)
    VALUES (v_s.user_id, encode(gen_random_bytes(32), 'hex'), v_new_refresh, now() + v_access_window, now() + v_refresh_window)
    RETURNING refresh_token, access_token, access_expires_at, refresh_expires_at
    INTO new_refresh_token, new_access_token, new_access_expires, new_refresh_expires;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (v_s.user_id, 'refresh', jsonb_build_object('old_session', v_s.id::text, 'new_refresh', new_refresh_token::text));

    RETURN NEXT;
END;
$$;


CREATE OR REPLACE FUNCTION auth_start_password_reset(p_email TEXT) RETURNS UUID LANGUAGE plpgsql AS $$
DECLARE
    v_user_id UUID;
    v_token UUID;
BEGIN
    SELECT id INTO v_user_id FROM users WHERE email = p_email;
    IF NOT FOUND THEN
        -- do not leak existence; return NULL or a dummy value
        RETURN NULL;
    END IF;

    INSERT INTO password_resets (user_id, expires_at)
    VALUES (v_user_id, now() + interval '1 hour')
    RETURNING token INTO v_token;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (v_user_id, 'password_reset_requested', jsonb_build_object('token', v_token::text));

    RETURN v_token;
END;
$$;


CREATE OR REPLACE FUNCTION auth_complete_password_reset(p_token UUID, p_new_password_hash TEXT) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    v_user_id UUID;
BEGIN
    SELECT user_id INTO v_user_id FROM password_resets WHERE token = p_token AND expires_at > now();
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    UPDATE users SET password_hash = p_new_password_hash WHERE id = v_user_id;
    DELETE FROM sessions WHERE user_id = v_user_id;

    DELETE FROM password_resets WHERE token = p_token;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (v_user_id, 'password_reset_completed', jsonb_build_object('token', p_token::text));

    RETURN TRUE;
END;
$$;


CREATE OR REPLACE FUNCTION auth_link_oauth(p_user_id UUID, p_provider TEXT, p_provider_user_id TEXT, p_payload JSONB) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO oauth_accounts (user_id, provider, provider_user_id, raw_payload)
    VALUES (p_user_id, p_provider, p_provider_user_id, p_payload)
    ON CONFLICT (provider, provider_user_id) DO NOTHING;

    INSERT INTO auth_audit (user_id, action, detail)
    VALUES (p_user_id, 'oauth_link', jsonb_build_object('provider', p_provider, 'provider_user_id', p_provider_user_id));

    RETURN TRUE;
EXCEPTION WHEN OTHERS THEN
    RETURN FALSE;
END;
$$;


CREATE OR REPLACE FUNCTION auth_delete_user(p_user_id UUID) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM oauth_accounts WHERE user_id = p_user_id;
    DELETE FROM sessions WHERE user_id = p_user_id;
    DELETE FROM email_verifications WHERE user_id = p_user_id;
    DELETE FROM password_resets WHERE user_id = p_user_id;
    DELETE FROM auth_audit WHERE user_id = p_user_id;
    DELETE FROM users WHERE id = p_user_id;

    RETURN TRUE;
END;
$$;


CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT,                -- nullable for OAuth-only accounts
    display_name TEXT,
    photo_url TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Email verification tokens
CREATE TABLE IF NOT EXISTS email_verifications (
    token UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Password reset tokens
CREATE TABLE IF NOT EXISTS password_resets (
    token UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Sessions table (conceptual access + refresh token storage)
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token TEXT NOT NULL,      -- opaque token (could be JWT in a real system)
    refresh_token UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

-- OAuth linked accounts
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,          -- e.g., 'google', 'facebook'
    provider_user_id TEXT NOT NULL,
    raw_payload JSONB,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

-- Audit / telemetry table
CREATE TABLE IF NOT EXISTS auth_audit (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id UUID,
    action TEXT NOT NULL,
    detail JSONB
);

-- Keep updated_at current
CREATE FUNCTION touch_user_updated() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_touch_user_updated ON users;
CREATE TRIGGER trg_touch_user_updated
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION touch_user_updated();


