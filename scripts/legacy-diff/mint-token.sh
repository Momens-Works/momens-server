#!/usr/bin/env bash
# 레거시 session_token 형식의 HS256 JWT 를 발급합니다(MOM-0877).
#
# 레거시 internal/platform/auth/jwt.go 의 Generate 와 같은 claim 만 씁니다: sub, iat, exp.
# ADR-0017 에 따라 신규 서버도 같은 서명 키로 이 토큰을 검증하므로, 한 값을 양쪽에 씁니다.
set -euo pipefail

sub="${1:?사용법: mint-token.sh <user-uuid> [secret]}"
secret="${2:-${MOMENS_DIFF_JWT_SECRET:?MOMENS_DIFF_JWT_SECRET 가 필요합니다}}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

now="$(date +%s)"
exp="$((now + 3600))"
header='{"alg":"HS256","typ":"JWT"}'
payload="{\"sub\":\"${sub}\",\"iat\":${now},\"exp\":${exp}}"

signing_input="$(printf '%s' "$header" | b64url).$(printf '%s' "$payload" | b64url)"
signature="$(printf '%s' "$signing_input" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)"

printf '%s.%s\n' "$signing_input" "$signature"
