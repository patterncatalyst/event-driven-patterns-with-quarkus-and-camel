#!/usr/bin/env bash
# Shared by verify.sh and bench.sh. Source it after setting APP, TOPIC and the COMPOSE array.

ratio() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.2f", (b > 0) ? a / b : 0 }'; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
end_offsets() { kafka kafka-get-offsets.sh --topic "$TOPIC" | awk -F: '{s += $3} END {print s + 0}'; }

# Producer metrics, summed over every client with this client id. A missing series counts as 0.
prom() { # prom <metric> <client id>
    curl -sf -G "http://localhost:19090/api/v1/query" \
        --data-urlencode "query=sum(${1}{client_id=\"${2}\"})" \
        | python3 -c "import json,sys; r=json.load(sys.stdin)['data']['result']; print(r[0]['value'][1] if r else 0)"
}
SENT=kafka_producer_record_send_total
REQUESTS=kafka_producer_request_total
BYTES=kafka_producer_outgoing_byte_total

snapshot() { # snapshot <client id>: sent, requests, bytes
    echo "$(prom $SENT "$1") $(prom $REQUESTS "$1") $(prom $BYTES "$1")"
}

# burst <profile> <orders> <concurrency>: runs one burst, waits until Prometheus has counted every record,
# then sets DELTA_SENT, DELTA_REQUESTS, DELTA_BYTES, RESULT and OFFSET_GROWTH.
burst() { # repeats a burst whose counters do not add up, as when a restarted app's old series is still visible
    local attempt
    for attempt in 1 2 3; do
        burst_once "$@"
        [[ "$DELTA_SENT" == "$2" ]] && (( DELTA_REQUESTS > 0 )) && return 0
        echo "  counters did not add up (attempt $attempt), repeating"
        sleep 10
    done
    return 0
}
burst_once() {
    local profile=$1 orders=$2 conc=$3 client="publisher-$1" before off_before
    local s0 r0 b0
    read -r s0 r0 b0 <<<"$(snapshot "$client")"
    off_before=$(end_offsets)
    RESULT=$(curl -s -X POST --max-time 900 "$APP/flash-sale?profile=$profile&orders=$orders&concurrency=$conc")
    OFFSET_GROWTH=$(( $(end_offsets) - off_before ))
    local s1 r1 b1 end=$((SECONDS + 90))
    while :; do
        read -r s1 r1 b1 <<<"$(snapshot "$client")"
        [[ "$(awk -v a="$s1" -v b="$s0" 'BEGIN {print a - b}')" == "$orders" ]] && break
        (( SECONDS < end )) || break
        sleep 2
    done
    DELTA_SENT=$(awk -v a="$s1" -v b="$s0" 'BEGIN {print a - b}')
    DELTA_REQUESTS=$(awk -v a="$r1" -v b="$r0" 'BEGIN {print a - b}')
    DELTA_BYTES=$(awk -v a="$b1" -v b="$b0" 'BEGIN {print a - b}')
    echo "  $profile, $orders orders, $conc callers: $DELTA_SENT records in $DELTA_REQUESTS requests ($(ratio "$DELTA_SENT" "$DELTA_REQUESTS") per request), $DELTA_BYTES bytes"
}
field() { python3 -c "import json,sys; print(json.load(sys.stdin)['$1'])" <<<"$RESULT"; }

