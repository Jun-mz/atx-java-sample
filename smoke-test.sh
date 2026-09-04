#!/bin/sh
# 서버가 떠 있는 상태에서 각 엔드포인트를 한 번씩 두드려 본다.
#   ./run.sh &  ./smoke-test.sh
BASE="http://localhost:${1:-8080}"
JAR=/tmp/was-smoke-cookies.txt
rm -f "$JAR"

printf '\n== GET / ==\n'
curl -sS -i "$BASE/" | head -n 8
printf '\n== GET /hello?name=클로드 ==\n'
curl -sS --get --data-urlencode "name=클로드" "$BASE/hello"; echo
printf '\n== GET /health ==\n'
curl -sS "$BASE/health"; echo

printf '\n-- 트리거 (a) 세션: 로컬 파일 --\n'
printf '== POST /login ==\n'
curl -sS -c "$JAR" -d 'userId=hong' "$BASE/login"; echo
printf '== GET /me ==\n'
curl -sS -b "$JAR" "$BASE/me"; echo
printf '== GET /me (쿠키 없이 → 401 기대) ==\n'
curl -sS -o /dev/null -w 'status=%{http_code}\n' "$BASE/me"

printf '\n-- 트리거 (b) 로컬 디스크: EBS RWO 후보 --\n'
printf '== POST /reports ==\n'
RID=$(curl -sS -b "$JAR" -X POST "$BASE/reports?period=202609" | sed -n 's/.*"reportId":"\([0-9a-f]*\)".*/\1/p')
echo "reportId=$RID"
printf '== GET /reports?id=%s ==\n' "$RID"
curl -sS -b "$JAR" "$BASE/reports?id=$RID"

printf '\n-- 트리거 (b) 공유 디스크: EFS RWX 후보 --\n'
printf '== POST /assets?name=catalog.json ==\n'
curl -sS -X POST -H 'Content-Type: application/json' -d '{"items":3}' "$BASE/assets?name=catalog.json"; echo
printf '== GET /assets ==\n'
curl -sS "$BASE/assets"; echo
printf '== GET /assets?name=catalog.json ==\n'
curl -sS "$BASE/assets?name=catalog.json"; echo

printf '\n-- 트리거 (a) IMDS --\n'
printf '== GET /instance (EC2 밖이면 unknown) ==\n'
curl -sS "$BASE/instance"; echo
printf '== GET /admin/config ==\n'
curl -sS "$BASE/admin/config"; echo

printf '\n-- 에러 경로 --\n'
printf '== GET /nope (404 기대) ==\n'
curl -sS -o /dev/null -w 'status=%{http_code}\n' "$BASE/nope"
printf '== DELETE /hello (405 기대) ==\n'
curl -sS -o /dev/null -w 'status=%{http_code}\n' -X DELETE "$BASE/hello"
printf '== 문서 루트 탈출 시도 (404 기대) ==\n'
curl -sS --path-as-is -o /dev/null -w 'status=%{http_code}\n' "$BASE/../../../etc/passwd"

rm -f "$JAR"
