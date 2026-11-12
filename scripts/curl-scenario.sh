#!/usr/bin/env bash
# Полный сценарий: login -> upload -> list -> rename -> download -> delete -> logout -> 401.
# Запуск: ./scripts/curl-scenario.sh [base_url]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "hello, cloud" > "$TMP/hello.txt"

check() { # check <ожидаемый код> <фактический код> <шаг>
  if [ "$1" != "$2" ]; then echo "FAIL: $3 вернул $2, ожидали $1"; exit 1; fi
  echo "OK:   $3 -> $2"
}

TOKEN=$(curl -s -X POST "$BASE/login" -H 'Content-Type: application/json' \
  -d '{"login":"user@example.com","password":"password"}' | sed -E 's/.*"auth-token":"([^"]+)".*/\1/')
[ -n "$TOKEN" ] || { echo "FAIL: login"; exit 1; }
echo "OK:   login -> token $TOKEN"
AUTH="auth-token: Bearer $TOKEN"

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/file?filename=hello.txt" -H "$AUTH" \
  -F "file=@$TMP/hello.txt" -F "hash=demo")
check 200 "$code" "upload"

list=$(curl -s "$BASE/list?limit=3" -H "$AUTH")
echo "      list: $list"
echo "$list" | grep -q '"filename":"hello.txt"' || { echo "FAIL: list"; exit 1; }

code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE/file?filename=hello.txt" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"name":"renamed.txt"}')
check 200 "$code" "rename"

curl -s -o "$TMP/out.txt" "$BASE/file?filename=renamed.txt" -H "$AUTH"
cmp -s "$TMP/hello.txt" "$TMP/out.txt" || { echo "FAIL: download (содержимое отличается)"; exit 1; }
echo "OK:   download -> содержимое совпадает"

code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/file?filename=renamed.txt" -H "$AUTH")
check 200 "$code" "delete"

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/logout" -H "$AUTH")
check 200 "$code" "logout"

code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/list?limit=3" -H "$AUTH")
check 401 "$code" "list после logout"

echo "Сценарий пройден."
