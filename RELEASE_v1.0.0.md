# Release v1.0.0

## 요약

이번 릴리즈는 운영 안정성, 보안 경계 강화, 수집/추천 로직 신뢰성 개선, 문서 재정비를 중심으로 진행했습니다.

## 주요 변경

1. 수집 로직 정확성 개선
- `WinningNumberCollectService` partial collect 결과 보존
- 미추첨 target 처리 시 수집된 결과/이벤트 누락 방지
- 종료 응답 생성 경로 공통화

2. 테스트 신뢰성 강화
- 미추첨 target 케이스 검증 보정
- truncated 검증 테스트 실효성 강화
- 관리자 토큰 필터 context path 케이스 추가

3. 관리자 API 보호 강화
- `AdminApiTokenFilter` 경로 판별 보완
- context path 환경 우회 가능성 대응

4. Docker/CD 안정성 개선
- `.dockerignore` 추가
- Dockerfile 빌드 단계 중복 정리
- DB 포트 외부 공개 기본 제거 (`expose`)
- CD를 CI 성공 이후 실행(`workflow_run`)으로 전환
- CD의 `chmod 777`, `fuser -k` 제거

5. 설정/로그 안정성 개선
- `.env` 로더 경고 로그 replay 보장
- JDBC URL 로그 마스킹 적용
- `required-config-validator` skip 설정 정합성 수정

6. 추천 규칙 설정화
- rule threshold를 설정으로 분리
  - `kraft.recommend.rules.birthday-threshold`
  - `kraft.recommend.rules.long-run-threshold`
  - `kraft.recommend.rules.decade-threshold`

7. 문서/구성 정리
- `README.md` 전면 재작성
- `LICENSE`(MIT) 추가
- 빈 파일 `LottoApiHealthIndicator.java` 제거

## 검증

- `./gradlew test` 통과

## 커밋

- `be759e0` 개선안 잔여 작업 완료 및 README 전면 개편
