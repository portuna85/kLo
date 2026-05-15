# Architecture

## Layers

- `feature`: 비즈니스 기능(추천, 당첨번호, 통계)
- `infra`: 보안/설정/헬스/웹 인프라
- `support`: 공통 응답/예외/에러 코드

## Main Flows

- 추천: `RecommendController -> RecommendService -> LottoRecommender`
- 수집: `WinningNumberCollectController -> LottoCollectionService -> LottoCollectionCommandService`
- 통계: `WinningNumberQueryService -> WinningStatisticsService`

## Data

- MariaDB + Flyway
- 핵심 테이블: `winning_numbers`, `lotto_fetch_logs`, `backfill_jobs`

## Scheduler/Lock

- 자동 수집 스케줄러 + ShedLock
- 단일 인스턴스/분산 환경 모두 중복 실행 방지
