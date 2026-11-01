#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/native-tests
"${CXX:-clang++}" -std=c++20 -Wall -Wextra -Werror -pthread \
  -I app/src/main/cpp app/src/test/cpp/zygisk_companion_test.cpp \
  app/src/main/cpp/zygisk/zygisk_companion.cpp -o build/native-tests/companion
build/native-tests/companion "${TMPDIR:-/tmp}"
