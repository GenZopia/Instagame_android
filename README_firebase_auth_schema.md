firebase_auth_schema.sql — README

Purpose
-------
This repository file demonstrates a standalone PostgreSQL schema and stored procedures that model common Firebase Authentication flows: user registration, email verification, sign-in/session issuance, refresh rotation, password reset, OAuth linking, and safe deletion.

Important
---------
- This is a purely local/educational SQL depiction. It does NOT connect to Firebase, Cloudflare Workers, or any external API.
- It uses PostgreSQL (pgcrypto extension). Run it in PostgreSQL (12+) to experiment.

Quick start
-----------
1. Open a PostgreSQL database (local or dev). Example using psql:

```bash
psql -d your_local_db -f firebase_auth_schema.sql
```

2. Try example flows from a SQL client. Example:

```sql
-- Register a user (returns verification token)
SELECT auth_register_user('bob@example.com','hashed_pw','Bob','https://example.com/bob.png');

-- Mark email verified (use returned token)
SELECT auth_verify_email('<token-uuid>');

-- Sign in (returns session data)
SELECT * FROM auth_sign_in_basic('bob@example.com','hashed_pw');
```

Notes & next steps
------------------
- Password handling here is simplified: the caller passes the "password hash" directly. In a real system you must use a secure password hashing algorithm (bcrypt/argon2) and perform comparisons in application code.
- Tokens shown here are opaque (random bytes). In production you may choose JWTs for access tokens, with short expiry and server-side refresh mechanics.
- Email sending and file storage are out of scope; integrate with mailers and storage layers as needed.

If you want, I can also:
- Add a tiny Node.js or Python script that runs some example flows against a local PostgreSQL instance.
- Add unit tests (pgTAP) for the functions.


