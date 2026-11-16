#!/usr/bin/env bash
# Runs the module tests, then altered copies of the route: each copy has one guard removed and must
# fail exactly the test that names that guard. Dev Services starts a fresh Kafka for every Maven run; no compose stack.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

ROUTE=notification-service/src/main/java/com/patterncatalyst/shipping/testing/OrderPlacedNotificationRoute.java
REPORTS=notification-service/target/surefire-reports
LOGS=target/verify-logs
TOTAL=0
PASSED=0

check() { # check "<description>" <command...>
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'FAIL %d: %s\n' "$TOTAL" "$desc"
    fi
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
sha() { sha256sum "$1" | cut -d' ' -f1; }
summary() { python3 -I results.py "$1" "$2"; } # summary <reports dir> <class>

rm -rf target/bites "$LOGS"
mkdir -p "$LOGS"
before=$(sha "$ROUTE")

mvn -B -ntp verify >"$LOGS/verify.log" 2>&1 || true
check "the unaltered module passes: the route test runs 4 tests and none fails" \
    expect "$(summary "$REPORTS" OrderPlacedRouteTest)" "ran=4 failed="
check "the advised route test runs 1 test, with no broker behind its Kafka ends, and passes" \
    expect "$(summary "$REPORTS" OrderPlacedAdviceTest)" "ran=1 failed="

# bite <name> <test that must fail>: apply one patch to a copy, run the broker-backed class, report what failed.
bite() {
    local name="$1" dir="target/bites/$1"
    mkdir -p "$dir"
    tar --exclude=./target --exclude=./bites --exclude='./*/target' -cf - . | tar -xf - -C "$dir"
    (cd "$dir" && patch -s -p1 <"../../../bites/$name.patch")
    mvn -B -ntp -f "$dir/pom.xml" verify -Dtest=OrderPlacedRouteTest >"$LOGS/bite-$name.log" 2>&1 || true
    summary "$dir/notification-service/target/surefire-reports" OrderPlacedRouteTest
}

dup=$(bite duplicate-allowed)
check "removing the dedup fails only the test that counts notifications for a repeated event" \
    expect "$dup" "ran=4 failed=aRepeatedEventYieldsExactlyOneNotification"
check "removing the dedup leaves the first-match test green, because it returns at the first record" \
    bash -c '[[ "$1" != *firstMatchSeesTheNotification* && "$1" == ran=4* ]]' _ "$dup"
check "removing the guard fails only the test that waits for a cancelled order's notification" \
    expect "$(bite guard-removed)" "ran=4 failed=aCancelledOrderGetsNoNotification"
check "keying by customer fails only the test that reads the key" \
    expect "$(bite key-changed)" "ran=4 failed=theNotificationIsKeyedByTheOrderId"
check "the module's own route is untouched after the bites" \
    expect "$(sha "$ROUTE")" "$before"

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
