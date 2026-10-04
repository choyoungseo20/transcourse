# Kafka 리밸런싱 비용 실험 (2026-10-04)

트랜스코딩 컨슈머 그룹 증설 시 할당 전략별 중복 인코딩 비용 비교.

## 조건

- 트랜스코딩 그룹 `job-transcoding`의 컨슈머 3대(app 1, worker 2), 파티션 6개
- 240초 720p 노이즈 영상 12건 업로드
- 3개 이상 파티션에서 트랜스코딩이 진행 중일 때 worker 1대 증설
- Range, Sticky, CooperativeSticky를 회차마다 번갈아 설정당 4회 반복
- Docker VM 메모리 16GB, 인스턴스 JVM 힙 512MB

## 결과

설정별 4회 평균. 회차별 값은 `results/aggregate_final.txt`.

| 항목 | Range | Sticky | CooperativeSticky |
|---|---|---|---|
| revoke 파티션 | 6 | 6 | 1 |
| 이동 파티션 | 2 (회차별 5, 1, 1, 1) | 1 | 1 |
| 중복 인코딩 건수 | 1.8 | 1 | 1 |
| 폐기된 인코딩 시간 (초) | 376.6 | 204.1 | 197.0 |
| 폐기 비율 | 16.2% (최대 35.7%) | 11.2% (최대 11.8%) | 11.3% (최대 11.8%) |
| 유지 파티션 재전달 후 SKIPPED | 3.5 | 4.5 | 0 |

폐기 비율은 폐기된 시도의 실행 시간을 배치 전체 트랜스코딩 실행 시간으로 나눈 값.

- 중복은 증설 시점에 인코딩 중이던 이동 파티션에서만 발생
- 유지 파티션의 재전달은 컨테이너의 pause 재적용으로 첫 시도 완료 뒤 도착해 전부 SKIPPED
- Range의 이동 수는 신규 멤버 ID의 정렬 위치에 따라 1~5개로 변동
- Sticky와 CooperativeSticky의 비용은 같고, 차이는 유지 파티션의 revoke 여부
- 남은 약 11%는 신규 멤버에게 넘기는 파티션 1개의 재처리 비용

## 재현

```bash
docker compose --profile scale up -d --scale worker=2
STRATEGIES="range sticky cooperative" VIDEO=/path/to/noisy.mp4 docs/experiments/kafka-rebalance/driver.sh
python3 docs/experiments/kafka-rebalance/aggregate.py
```

테스트 영상 생성:

```bash
ffmpeg -f lavfi -i "testsrc2=size=1280x720:rate=30,noise=alls=50:allf=t+u" -f lavfi -i "sine=frequency=440:sample_rate=44100" \
  -t 240 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac noisy.mp4
```

원본 데이터는 MySQL `rebalance_event`, `worker_execution` 테이블. 회차 경계는 `results/runs.txt`.

세 처리 유형이 한 컨슈머 그룹을 공유하던 구조의 2026-09-23 결과는 `results/2026-09-23/`.
