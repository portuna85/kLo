# Security Policy

## Reporting

보안 이슈는 공개 이슈로 등록하지 말고 저장소 관리자에게 비공개로 전달합니다.

## Scope

- 인증/인가 (`/admin/**`, 토큰 필터)
- 입력 검증/예외 응답
- 보안 헤더(CSP/HSTS/COOP/CORP)
- 민감정보 로그 노출

## Token Policy

- 운영은 `KRAFT_ADMIN_API_TOKEN_HASHES` 우선
- 평문 토큰(`KRAFT_ADMIN_API_TOKENS`)은 로컬/임시 환경 전용
- 토큰은 최소 32자 이상 랜덤 문자열 권장

## Hardening Baseline

- `SPRING_PROFILES_ACTIVE=prod`
- `KRAFT_SECURITY_PUBLIC_PROMETHEUS` 정책 명시
- Flyway `clean-disabled=true`
- 배포 후 `/actuator/health/readiness` 확인
