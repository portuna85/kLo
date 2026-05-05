# kLo (KraftLotto) — 모듈 단위 개선 로드맵

> 작성일: 2026-05-04 | 저장소: `github.com/portuna85/kLo` | 외부 접속: `https://kraft.io.kr`

---

## 📌 프로젝트 개요

로또 6/45 추천 웹 서비스 (개인 사이드 프로젝트, Kraft와는 별개 트랙)

### 기술 스택

| 구분 | 내용 |
|------|------|
| 언어/런타임 | Java 25 + Spring Boot 4.0.5 |
| 빌드 | Gradle 9.4.1 (Kotlin DSL) |
| DB | MariaDB 11.8 |
| 뷰 | Thymeleaf |
| 컨테이너 | Docker Compose |
| 아키텍처 검증 | ArchUnit |

### 인프라

| 구분 | 내용 |
|------|------|
| 서버명 | kraft-server (Ubuntu 26.04) |
| 내부 IP | 192.168.0.28 |
| 공인 IP | 49.143.105.191 (ipTIME N602SR) |
| 웹 서버 | Nginx 1.28.3 + Let's Encrypt SSL |
| CI | GitHub Actions, develop 브랜치 → GitHub-hosted Runner |
| CD | main 브랜치 → Self-hosted Runner, 승인 후 docker compose 배포 |

### 현재 상태 진단

- ✅ **강점**: 백엔드 Hexagonal 계층화 양호
- ⚠️ **약점**: `app.js` God File 상태 (모든 프론트 로직 단일 파일)
- 📋 **이슈 관리**: deep research 보고서 → GitHub Issue 9개로 변환 완료
- 🏷️ **라벨 5축**: `type` / `area` / `priority(P0~P3)` / `effort(S~L)` / `risk`

---

## 🎯 마일스톤별 모듈

### M1 · 보안 (P0 — 즉시)

> 외부 접속이 이미 열린 상태이므로 가장 먼저 처리해야 할 영역

#### `#1` X-Forwarded-For 신뢰 설정
- **effort**: S | **risk**: 중
- Nginx 단에서 `set_real_ip_from`으로 실제 클라이언트 IP만 신뢰
- Spring Boot `server.forward-headers-strategy=NATIVE` 동시 적용
- 잘못된 IP 신뢰 시 레이트리밋·로그·차단 정책 모두 무력화됨

#### `#2` 관리자 엔드포인트 분리
- **effort**: M | **risk**: 고
- `/admin/**` 별도 SecurityFilterChain 등록 (`@Order` 우선순위 명시)
- IP 화이트리스트 + Basic Auth 또는 별도 인증
- 일반 사용자 영역과 인증·인가 정책 완전 분리

#### `#3` `byRound` 파라미터 검증
- **effort**: S | **risk**: 중
- ConstraintValidator로 회차 범위·형식 강제 (`@Min`, `@Max`, `@Pattern`)
- DTO 레벨 + 컨트롤러 진입 시점 이중 검증
- SQL Injection·DoS 패턴 차단

---

### M2 · 품질 게이트 (P1)

> PR 머지 전 자동 검증 파이프라인 구축

#### `#4` Spotless + Checkstyle + JaCoCo 80%
- **effort**: M | **risk**: 저
- Spotless: 코드 포맷 자동 통일
- Checkstyle: 네이밍·구조 규칙
- JaCoCo: 커버리지 80% 임계치, 미달 시 빌드 실패
- Gradle 단일 태스크(`./gradlew check`)로 통합

#### `#5` GitHub Actions + Dependabot + CodeQL + SBOM
- **effort**: M | **risk**: 저
- Actions: develop 푸시 시 자동 빌드·테스트
- Dependabot: 의존성 취약점 주간 PR
- CodeQL: 정적 보안 분석
- SBOM(CycloneDX): 의존성 명세 산출, 공급망 보안 대응

---

### M3 · 프론트엔드 구조 (P1)

#### `#6` `app.js` God File 분해
- **effort**: L | **risk**: 중
- 현재 단일 파일 → ES Module 4분할

| 모듈 | 역할 | 의존 |
|------|------|------|
| `api.js` | 서버 통신·fetch 래퍼 | (없음) |
| `state.js` | 상태 보관·구독자 알림 | (없음) |
| `ui.js` | DOM 조작·렌더링 | state.js |
| `main.js` | 진입점·이벤트 바인딩 | api·ui·state |

- 단계: ① 함수 그룹핑 → ② 파일 분리 → ③ import/export 정리 → ④ 회귀 테스트

---

### M4 · 확장성 (P2)

#### `#7` 레이트리밋
- **effort**: M | **risk**: 저
- **단기**: Caffeine 인메모리 (단일 인스턴스 한정)
- **중기**: Redis 전환 준비 (멀티 인스턴스·재시작 영속)
- IP·세션·엔드포인트별 차등 정책

#### `#8` 캐시 점증 갱신
- **effort**: M | **risk**: 저
- 로또 회차 데이터 TTL 기반 갱신
- DB 풀스캔 → 신규 회차 차분 적재 (delta update)
- 매주 토요일 추첨 직후 자동 동기화 트리거

---

### M5 · 기술 부채 (P3)

#### `#9` JDK 25 ADR 작성
- **effort**: S | **risk**: 저
- ADR(Architecture Decision Record) 형식으로 근거 문서화
- Virtual Thread 활용 영역·기준
- Sequenced Collections 적용 사례
- LTS 정책·Eclipse Temurin/Liberica JDK 선택 이유 포함

---

## 📊 우선순위 실행 순서

| 순서 | Issue | 모듈 | Priority | Effort | 핵심 가치 |
|------|-------|------|----------|--------|----------|
| 1 | #1 | M1 보안 | P0 | S | 잘못된 IP 신뢰 차단 |
| 2 | #3 | M1 보안 | P0 | S | 입력 검증 강화 |
| 3 | #2 | M1 보안 | P0 | M | 관리자 영역 격리 |
| 4 | #4 | M2 품질 | P1 | M | 자동 검증 기반 |
| 5 | #5 | M2 품질 | P1 | M | 공급망 보안 |
| 6 | #6 | M3 프론트 | P1 | L | 유지보수성 회복 |
| 7 | #7 | M4 확장 | P2 | M | 트래픽 방어 |
| 8 | #8 | M4 확장 | P2 | M | DB 부하 절감 |
| 9 | #9 | M5 부채 | P3 | S | 기술 결정 근거 |

> **추천 진행 방식**: P0(M1) 3건을 한 스프린트에 묶어 처리 → P1(M2·M3)을 2주 단위로 → 이후 P2·P3 순차

---

## 🔄 다음 단계 점검 포인트

1. **M1 완료 시점에 보안 헤더(CSP, HSTS, X-Frame-Options) 추가 검토**
2. **M2 도입 후 PR 템플릿·CODEOWNERS 정비**
3. **M3 분해 완료 후 빌드 단계에 esbuild/Vite 도입 여부 결정**
4. **M4 캐시 도입 시 부하 테스트(k6 또는 JMeter) 베이스라인 측정**
5. **M5 ADR 양식 표준화 후 다른 결정도 소급 문서화**
