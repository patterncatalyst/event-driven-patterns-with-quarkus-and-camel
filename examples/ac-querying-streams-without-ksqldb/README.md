# Appendix C: Querying streams without ksqlDB

One question, asked of three open source engines on the same input: deliveries per carrier per tumbling minute of event time, from `edp.shipping.delivered`. Kafka Streams answers through an interactive query on a window store (`delivery-counts-service`, `GET /counts`). Flink SQL writes closed windows to the upsert-kafka topic `edp.shipping.delivery-counts-flink`. RisingWave keeps two materialized views, `counts_rw` (updating) and `counts_rw_final` (`EMIT ON WINDOW CLOSE`, which RisingWave marks experimental), queried with `psql` from the PostgreSQL container. No premium feature is used.

The lateness is one number, `LATENESS_SECONDS` (default 10): the Kafka Streams grace and both watermark delays.

| Setting | Default | Effect |
|---|---|---|
| `LATENESS_SECONDS` | `10` | The lateness in all three engines |
| `KS_GRACE_SECONDS` | `LATENESS_SECONDS` | Demo code. Overrides the grace of Kafka Streams alone |
| `KEEP` | `0` | `1` leaves the stack running |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # the topology on the Kafka Streams test driver
./verify.sh        # compose stack
KS_GRACE_SECONDS=0 ./verify.sh
```

Flink UI: http://127.0.0.1:18081. RisingWave: `docker exec -e PGPASSWORD= edp-postgres psql -h risingwave -p 4566 -U root -d dev`.

## What verify.sh does

Event times are seconds after a minute boundary ten minutes in the past (`counts.py`).

1. Preconditions: topics with one partition; the service ready; the Flink job RUNNING; the RisingWave views present.
2. Checks the lateness is the same number in the Kafka Streams log and both rendered SQL files.
3. Sends 14 records (FASTLINE and AIRCO, three windows, one record 7 s behind the newest, a fourth window that opens). Each engine's closed windows equal FASTLINE 4/2/1 and AIRCO 2/3/1; Flink's topic and `counts_rw_final` hold no open window; the three are equal; open windows answer in Kafka Streams and `counts_rw`.
4. Sends a late record for window 1 and a record that closes window 4. Window 1 stays 4 everywhere; Kafka Streams logs the skip; Flink's topic holds 7 records; the three stay equal.
5. A trace in Tempo and logs in Loki.

## Checks

1 topics; 2 service ready; 3 Flink job RUNNING; 4 RisingWave views; 5 same lateness; 6 Kafka Streams closed windows; 7 Flink topic; 8 `counts_rw`; 9 `counts_rw_final`; 10 three equal; 11 out-of-order record counted; 12 open windows answer; 13 Kafka Streams late record; 14 Flink late record; 15 RisingWave late record; 16 three equal after the late record; 17 Tempo; 18 Loki. Prints `18/18 checks passed`.

## Experiments

| Switch | Predicted failing checks |
|---|---|
| `KS_GRACE_SECONDS=0 ./verify.sh` (demo code) | 5, 6, 10, 11, 13, 16 (12 of 18 pass): the 55 s record is late in Kafka Streams alone, so FASTLINE window 1 is 3 there and 4 elsewhere |

Frozen after the first window run.

## Measurements

From the first spike; the window re-measures.

| Item | Value |
|---|---|
| Last record to closed windows, Kafka Streams | TBD(spike: 0.05 s) |
| Flink | TBD(spike: 2.9 s) |
| RisingWave | TBD(spike: 3.0 s) |
| RisingWave `EMIT ON WINDOW CLOSE` | TBD(spike: 3.8 s) |
| Peak memory of all containers | TBD(spike: 2.12 GiB) |
| Flink job submit | TBD(spike: 4.9 s) |
| RisingWave DDL | TBD(spike: 2.4 s) |

## Layout

| Path | Purpose |
|---|---|
| `delivery-counts-service/` | Topology, REST resource, topology tests, Containerfile |
| `sql/flink.sql`, `sql/risingwave.sql` | The statements, with the lateness placeholder |
| `counts.py` | Input records and the normalizer of each engine's answer |
| `compose.yaml` | Topics, the service, and `_infra` Kafka, PostgreSQL, LGTM, Flink, RisingWave |

## Tests

`mvn -B verify` runs 7 tests on the Kafka Streams test driver: counts per window by event time, the out-of-order record counted, a late record dropped and counted, grace 0 counting one less, closed windows queryable for the retention, and the default retention losing them (comparison code).
