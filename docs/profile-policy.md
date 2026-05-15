# Profile Policy

## local

- 개발 편의 우선
- mock API 기본
- 상세 actuator 노출 가능

## prod

- 실제 API 사용 기본
- 보안 헤더 및 인증 정책 강제
- 배포 전/후 readiness 확인 필수

## Rules

- CI/CD는 `prod` 기준으로 검증
- 로컬 편의 설정을 prod에 반영 금지
- profile 전환 시 필수 환경변수 검증 수행
- `KRAFT_ENV`는 `local` 또는 `prod`만 허용
- `KRAFT_ENV`와 `SPRING_PROFILES_ACTIVE`는 동일 의미로 정합해야 함
