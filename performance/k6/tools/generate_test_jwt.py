#!/usr/bin/env python3
"""Generate local-only HS256 JWTs for the k6 performance fixture.

The token values are written to an ignored env file and are never printed.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import secrets
import stat
import time
from pathlib import Path


DEFAULT_USER_SUBJECT = "00000000-0000-4000-8000-000000000001"
DEFAULT_ADMIN_SUBJECT = "00000000-0000-4000-8000-999999999999"
DEFAULT_ASSIGNMENT_ID = "10000000-0000-4000-8000-000000000001"
DEFAULT_COURSE_SLUG = "perf-k6"
DEFAULT_ISSUER = "http://localhost:9000"
DEFAULT_AUDIENCE = "aandi-gateway"
DEFAULT_SECRET = "local-dev-jwt-secret-must-be-at-least-32-bytes"
DEFAULT_TTL_SECONDS = 14_400


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate local k6 JWT env file without printing token values.")
    parser.add_argument("--output", default="performance/k6/env.local", help="Ignored env file path to write.")
    parser.add_argument("--user-subject", default=os.getenv("PERF_USER_ID", DEFAULT_USER_SUBJECT))
    parser.add_argument("--admin-subject", default=os.getenv("PERF_ADMIN_USER_ID", DEFAULT_ADMIN_SUBJECT))
    parser.add_argument("--course-slug", default=os.getenv("COURSE_SLUG", DEFAULT_COURSE_SLUG))
    parser.add_argument("--assignment-id", default=os.getenv("ASSIGNMENT_ID", DEFAULT_ASSIGNMENT_ID))
    parser.add_argument("--issuer", default=os.getenv("AUTH_ISSUER_URI", DEFAULT_ISSUER))
    parser.add_argument("--audience", default=os.getenv("AUTH_AUDIENCE", DEFAULT_AUDIENCE))
    parser.add_argument("--secret", default=os.getenv("AUTH_JWT_SECRET", DEFAULT_SECRET))
    parser.add_argument("--ttl-seconds", type=int, default=int(os.getenv("PERF_JWT_TTL_SECONDS", str(DEFAULT_TTL_SECONDS))))
    args = parser.parse_args()

    if len(args.secret.encode("utf-8")) < 32:
        raise SystemExit("AUTH_JWT_SECRET must be at least 32 bytes.")
    if args.ttl_seconds <= 0:
        raise SystemExit("--ttl-seconds must be positive.")

    now = int(time.time())
    user_token = create_access_token(
        secret=args.secret,
        issuer=args.issuer,
        audience=args.audience,
        subject=args.user_subject,
        role="USER",
        issued_at=now,
        expires_at=now + args.ttl_seconds,
    )
    admin_token = create_access_token(
        secret=args.secret,
        issuer=args.issuer,
        audience=args.audience,
        subject=args.admin_subject,
        role="ADMIN",
        issued_at=now,
        expires_at=now + args.ttl_seconds,
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    content = "\n".join(
        [
            "BASE_URL=http://localhost:8080",
            f"ACCESS_TOKEN={shell_escape(user_token)}",
            f"ADMIN_ACCESS_TOKEN={shell_escape(admin_token)}",
            f"COURSE_SLUG={args.course_slug}",
            f"ASSIGNMENT_ID={args.assignment_id}",
            "TARGET_ENVIRONMENT=local",
            "ALLOW_REMOTE_LOAD_TEST=false",
            "RESULT_DIR=performance/results",
            "",
        ]
    )
    output.write_text(content, encoding="utf-8")
    output.chmod(stat.S_IRUSR | stat.S_IWUSR)
    print(f"Wrote local k6 env file: {output}")
    print("Token values were not printed. The file is ignored by Git.")
    return 0


def create_access_token(
    *,
    secret: str,
    issuer: str,
    audience: str,
    subject: str,
    role: str,
    issued_at: int,
    expires_at: int,
) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "iss": issuer,
        "sub": subject,
        "aud": [audience],
        "role": role,
        "token_type": "ACCESS",
        "jti": secrets.token_hex(16),
        "iat": issued_at,
        "exp": expires_at,
    }
    signing_input = f"{b64url_json(header)}.{b64url_json(payload)}"
    signature = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
    return f"{signing_input}.{b64url(signature)}"


def b64url_json(value: object) -> str:
    return b64url(json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8"))


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def shell_escape(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


if __name__ == "__main__":
    raise SystemExit(main())
