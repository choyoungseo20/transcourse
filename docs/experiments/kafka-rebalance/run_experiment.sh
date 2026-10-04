#!/bin/zsh
# usage: run_experiment.sh <label> <videos>
set -e
S=$(cd "$(dirname "$0")" && pwd)
cd "$S/../../.."
LABEL=$1; N=${2:-12}
Q=$S/q.sh
T0=$($Q "select now(6)" | tail -1)
FIRST=$($Q "select coalesce(max(id),0)+1 from video" | tail -1)
echo "[$LABEL] t0=$T0 first_video_id=$FIRST"
for i in $(seq 1 $N); do
  curl -s -o /dev/null -w "upload $i http=%{http_code}\n" -F "file=@${VIDEO:?VIDEO=<테스트 영상 경로>}" http://localhost:8080/videos
done
# 4개 이상 파티션에서 TRANSCODING이 진행 중일 때까지 대기
while true; do
  BUSY=$($Q "select count(distinct e.partition_no) from worker_execution e join processing_job j on j.id=e.job_id where e.finished_at is null and j.type='TRANSCODING' and e.received_at>='$T0'" | tail -1)
  echo "$(date +%T) busy transcoding partitions=$BUSY"
  [ "$BUSY" -ge 4 ] && break
  sleep 3
done
TS=$($Q "select now(6)" | tail -1)
echo "[$LABEL] scale-up at $TS"
docker compose -f docker-compose.yaml -f $S/override.yaml --profile scale up -d --scale worker=3 --no-recreate worker 2>&1 | grep -E "Started|Created" || true
# 모든 job 종료 대기
while true; do
  LEFT=$($Q "select count(*) from processing_job where video_id>=$FIRST and status in ('PENDING','RUNNING')" | tail -1)
  INFL=$($Q "select count(*) from worker_execution where finished_at is null and received_at>='$T0'" | tail -1)
  [ "$LEFT" = "0" ] && [ "$INFL" = "0" ] && break
  sleep 10
done
TE=$($Q "select now(6)" | tail -1)
echo "[$LABEL] done at $TE"
echo "$LABEL|$T0|$TS|$TE|$FIRST" >> $S/results/runs.txt
