# Kafka 리밸런싱 비용 실험 (2026-09-23)

컨슈머 증설 시 RangeAssignor(Eager)와 CooperativeStickyAssignor의 중복 인코딩 비용 비교.

아래 결과는 세 처리 유형이 하나의 컨슈머 그룹을 공유하던 구조에서 측정한 값이다.

## 조건

- 컨슈머 3대(app 1 + worker 2), 파티션 6개, 480초 720p 노이즈 영상 12건 업로드
- 트랜스코딩이 4개 파티션 이상에서 진행 중일 때 worker 1대 증설
- 설정당 4회 반복
- 진행 중 인코딩의 좀비 판정 방지를 위한 `running-timeout` 30m 상향

## 결과

설정별 4회 평균. 회차별 값은 `results/aggregate_final.txt`.

| 항목 | Range | CooperativeSticky |
|---|---|---|
| revoke 파티션 | 6.0 | 1.0 |
| 이동 파티션 | 2.5 (1~5) | 1.0 |
| 중복 인코딩 건수 | 2.0 | 0.75 |
| 폐기된 인코딩 시간 (초) | 844.5 | 250.5 |
| 폐기 비율 (폐기 시간 / 배치 전체 트랜스코딩 시간) | 18.0% | 7.2% |
| 유지 파티션 재읽기 후 skip | 3.0 | 0 |

- 유지 파티션은 재전달되어도 컨테이너가 다시 pause해 첫 시도 완료 후 SKIPPED. 폐기 연산은 전부 이동 파티션에서 발생.
- Range의 이동 수는 신규 멤버 ID의 정렬 위치에 따라 1~5개로 흔들림.

## 재현

```bash
docker compose --profile scale up -d --scale worker=2
VIDEO=/path/to/noisy.mp4 docs/experiments/kafka-rebalance/driver.sh      # 설정별 4회
python3 docs/experiments/kafka-rebalance/aggregate.py                     # results/runs.txt 기준 집계
```

테스트 영상 생성:

```bash
ffmpeg -f lavfi -i "testsrc2=size=1280x720:rate=30,noise=alls=50:allf=t+u" -f lavfi -i "sine=frequency=440:sample_rate=44100" \
  -t 480 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac noisy.mp4
```

원본 데이터는 MySQL `rebalance_event`, `worker_execution` 테이블. 회차 경계는 `results/runs.txt`.
