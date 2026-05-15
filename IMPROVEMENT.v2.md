# kraft-lotto 슬림 재구축 실행안 v2
작성일: 2026-05-16
기준 문서: IMPROVEMENT.md

## 완료 현황 (2026-05-16)
- 완료: 의존성 슬림화 및 `app.jar` 단일 산출물 통일
- 완료: 설정 정리(`application*.yml` 공개 MVC 기준)
- 완료: 보안/관리/API 인프라 제거(`/api/**`, `/admin/**` 제거)
- 완료: MVC 5개 화면 전환(`/`, `/recommend`, `/rounds/search`, `/frequency`, `/rounds`)
- 완료: 조회 서비스 단순화(`getLatest/getByRound/list`)
- 완료: 빈도 서비스 단순화(1~45 전수, 미출현 0, bonus 제외 집계)
- 완료: 추천 화면 정리(규칙/면책 문구 노출, count 범위 처리)
- 완료: 스케줄러 단순화(토 21:10/22:10, 일 06:10, Service 직접 호출)
- 완료: DhLotteryApiClient 방어 로직(HTTP 오류/blank/non-json/HTML 방어, retry/timeout)
- 완료: 템플릿/도커/운영 스크립트 정리
- 완료: 운영 검증(`docker compose build app && docker compose up -d`, 5개 URL 렌더링 200 확인)
- 완료: `scripts/add-winning-number.sh` 리허설(문법 체크, INSERT 성공, 중복 차단 확인)
- 완료: 품질 게이트(`./gradlew clean test`, `./gradlew clean build`)

## 남은 TODO
- 배포 대상 환경의 액세스 로그에서 `/api/**` 외부 호출 의존 최종 확인 후 배포 진행

## 완료 정의(Definition of Done)
- 공개 기능이 5개 화면으로 고정됨
- REST 공개 API/관리 API/보안 인프라 제거 완료
- Scheduler가 Service를 직접 호출
- 수집 실패 시 수동 등록 스크립트로 복구 가능
- 도커 기동 및 Flyway 마이그레이션 정상
