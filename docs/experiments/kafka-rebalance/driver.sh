#!/bin/zsh
S=$(cd "$(dirname "$0")" && pwd)
cd "$S/../../.."
KT="docker exec transcourse-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --describe --group video-jobs"
restart_consumers() {
  local strategy=$1
  docker compose --profile scale stop app worker >/dev/null 2>&1
  docker compose --profile scale rm -f app worker >/dev/null 2>&1
  KAFKA_ASSIGNMENT_STRATEGY=$strategy docker compose -f docker-compose.yaml -f $S/override.yaml --profile scale up -d --scale worker=2 app worker >/dev/null 2>&1
  for i in $(seq 1 60); do curl -sf -o /dev/null http://localhost:8080/swagger-ui/index.html && break; sleep 3; done
  for i in $(seq 1 60); do
    eval "$KT --state" 2>/dev/null | grep -q "Stable *3" && break
    sleep 3
  done
  eval "$KT --state" 2>/dev/null | tail -1
}
for round in 1 2 3 4; do
  for strat in range cooperative; do
    if [ $strat = range ]; then cls=org.apache.kafka.clients.consumer.RangeAssignor; else cls=org.apache.kafka.clients.consumer.CooperativeStickyAssignor; fi
    echo "=== $strat-$round restart"
    restart_consumers $cls
    export KAFKA_ASSIGNMENT_STRATEGY=$cls
    VIDEO=$VIDEO $S/run_experiment.sh $strat-$round 12
  done
done
echo "ALL DONE"
