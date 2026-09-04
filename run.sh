#!/bin/sh
# SimpleWAS 빌드 & 실행 (Maven 없이 javac 만으로 동작)
#
#   ./run.sh          기본 포트 8080
#   ./run.sh 9090     포트 지정
set -e

cd "$(dirname "$0")"
PORT="${1:-8080}"
OUT=build/classes

rm -rf build
mkdir -p "$OUT" build/webapp

echo "[1/3] compiling for Java 8..."
find src/main/java -name '*.java' > build/sources.txt
# JDK 9+ 라면 --release 8 이 부트클래스패스까지 8 로 맞춰준다.
# JDK 8 자체로 빌드할 때는 --release 를 모르므로 -source/-target 으로 떨어진다.
if ! javac --release 8 -encoding UTF-8 -d "$OUT" @build/sources.txt 2>/dev/null; then
  javac -source 1.8 -target 1.8 -encoding UTF-8 -d "$OUT" @build/sources.txt
fi

echo "[2/3] copying static resources..."
cp -R src/main/resources/webapp/. build/webapp/

echo "[3/3] starting SimpleWAS on port ${PORT}..."
exec java -cp "$OUT" com.example.was.WasServer "$PORT"
