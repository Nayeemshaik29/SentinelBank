#!/usr/bin/env bash
# Creates the SentinelBank topics. Safe to re-run (--if-not-exists).
# Each business topic gets a .retry and a .dlt companion (retry, then dead-letter).
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:9092}"
KT=/opt/kafka/bin/kafka-topics.sh

for base in transfer.initiated transfer.completed transfer.failed; do
  for topic in "$base" "$base.retry" "$base.dlt"; do
    "$KT" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
      --topic "$topic" --partitions 3 --replication-factor 1
  done
done

echo "Topics:"
"$KT" --bootstrap-server "$BOOTSTRAP" --list
