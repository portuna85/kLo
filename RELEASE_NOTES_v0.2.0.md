## v0.2.0 릴리즈 노트 (초안)

### 주요 변경사항
- 로깅 파이프라인 리팩토링: `logs/kraft.log`, `logs/kraft-error.log`, `logs/kraft-warn.log`, `logs/kraft-debug.log` 분리 및 롤링/아카이브 정책 정비.
- Spring Boot 4 환경 초기화 강화:
  - `.env` 자동 로드(`EnvironmentPostProcessor`) 도입
  - 필수 설정값 검증(`RequiredConfigValidator`) 및 부팅 실패 메시지 개선
  - 로컬 실행 시 DB 호스트 자동 보정(`DatasourceUrlAutoFixer`) 추가
- 보안/웹 개선:
  - CSP/Referrer/Permissions 정책 헤더 적용
  - 메인 페이지 UX 개선(회차 목록 페이지네이션, ADMIN 트리거 모달, JS 로깅)

### 운영/개발 메모
- 로컬 `bootRun` 실행 시 `.env`의 `KRAFT_DB_URL`이 docker 서비스명(`mariadb`)인 경우 자동 보정 로직이 동작합니다.
- 자동 보정 비활성화: `KRAFT_DB_HOST_REWRITE=false`
- 로컬 DB 호스트 강제 지정: `KRAFT_DB_LOCAL_HOST=localhost`

### 호환성
- `application-prod.yml` 제거.
- 환경 변수 기반 설정을 기본으로 사용합니다 (`.env.example` 참고).

### 체크리스트
- [x] 태그 `v0.2.0` 생성/푸시
- [x] 릴리즈 생성
- [ ] 릴리즈 노트 최종 문구 검토

