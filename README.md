# 🎰 Kraft Lotto (kLo)

> **Spring Boot 4 기반의 고성능 로또 데이터 관리 및 추천 엔진**  
> 정교한 패턴 분석을 통한 번호 추천, 동행복권 API 연동 수집, 그리고 엄격한 아키텍처 검증을 지향하는 프로젝트입니다.

---

## 📌 목차
- [✨ 주요 기능](#-주요-기능)
- [🛠️ 기술 스택](#-기술-스택)
- [🏗️ 아키텍처 및 설계 원칙](#-아키텍처-및-설계-원칙)
- [🚀 빠른 시작](#-빠른-시작)
- [📡 API 레퍼런스](#-api-레퍼런스)
- [🧪 테스트 및 품질 관리](#-테스트-및-품질-관리)
- [📄 라이선스](#-라이선스)

---

## ✨ 주요 기능

| 기능 | 설명 | 주요 엔드포인트 |
| :--- | :--- | :--- |
| 🎯 **번호 추천** | 편향된 패턴(생일, 연대, 과거 당첨 등) 회피 로직 기반 추천 | `POST /api/recommend` |
| 🔄 **자동 수집** | 스케줄러를 통한 매주 토요일 최신 당첨 번호 자동 동기화 | - |
| 📊 **통계 분석** | 번호별 출현 빈도 및 요약 통계 제공 | `/api/winning-numbers/stats/**` |
| 🔧 **관리 도구** | 특정 회차 수동 수집, 범위 백필(Backfill) 및 작업 모니터링 | `/admin/lotto/**` |
| 📖 **문서화** | Spring REST Docs를 이용한 타입 세이프한 API 명세 제공 | `/docs/index.html` |

---

## 🛠️ 기술 스택

| 분류 | 기술 |
| :--- | :--- |
| **Core** | Java 25, Spring Boot 4.0.5 |
| **Data** | Spring Data JPA, Hibernate, MariaDB 11.8, Flyway |
| **Cache** | Caffeine Cache (Local), Redis (Rate Limit) |
| **Security** | Spring Security (Token based Admin API), Rate Limiting |
| **Observability** | Spring Boot Actuator, Micrometer, OTLP Tracing |
| **Testing** | JUnit 5, AssertJ, Testcontainers, ArchUnit, Mockito |

---

## 🏗️ 아키텍처 및 설계 원칙

본 프로젝트는 서비스의 안정성과 확장성을 위해 다음과 같은 설계 원칙을 준수합니다.

1.  **계층형 아키텍처 (Layered Architecture)**: Web, Application, Domain, Infrastructure 계층을 엄격히 분리합니다.
2.  **도메인 중심 설계 (Domain-Driven Design)**: 비즈니스 핵심 로직은 프레임워크 의존성이 없는 순수 도메인 객체에 위치합니다.
3.  **데이터 무결성**: DB 수준의 `CHECK` 제약 조건을 통해 잘못된 데이터(정렬되지 않은 번호, 범위 초과 등)의 유입을 원천 차단합니다.
4.  **자동화된 구조 검증**: `ArchUnit`을 사용하여 아키텍처 위반 사항을 테스트 단계에서 검증합니다.

---

## 🚀 빠른 시작

### 1️⃣ 환경 변수 설정
`.env.example` 파일을 복사하여 `.env` 파일을 생성하고 필수 값을 입력합니다.

```bash
cp .env.example .env
```

### 2️⃣ 필수 환경 변수 입력
운영(`prod`) 프로파일 기동을 위해 아래 값들을 설정해야 합니다.
- `KRAFT_DB_PASSWORD`: 데이터베이스 암호
- `KRAFT_ADMIN_API_TOKENS`: 관리자 API 접근을 위한 토큰 목록 (쉼표 구분)

### 3️⃣ Docker를 이용한 실행
```bash
docker compose up -d --build
```

### 4️⃣ 서비스 접속
- **애플리케이션**: [http://localhost:8080/](http://localhost:8080/)
- **API 문서**: [http://localhost:8080/docs/index.html](http://localhost:8080/docs/index.html)
- **헬스 체크**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## 📡 API 레퍼런스

### 🔓 Public API (사용자용)
- `POST /api/recommend`: 맞춤형 로또 번호 추천 조합 생성
- `GET /api/winning-numbers/latest`: 최신 회차 당첨 정보 조회
- `GET /api/winning-numbers/stats/frequency`: 번호별 출현 빈도 통계

### 🔒 Admin API (관리자용)
> `X-Kraft-Admin-Token` 헤더가 필요합니다.
- `POST /admin/lotto/draws/collect-next`: 다음 회차 즉시 수집
- `POST /admin/lotto/jobs/backfill?from=1&to=1000`: 비동기 대량 데이터 백필

---

## 🧪 테스트 및 품질 관리

다각도의 테스트 전략을 통해 소프트웨어 결함을 최소화합니다.

- **단위 테스트**: 비즈니스 로직 및 도메인 규칙 검증
- **통합 테스트 (IT)**: `Testcontainers`와 실제 `MariaDB`를 이용한 레포지토리 및 DB 제약 조건 검증
- **아키텍처 테스트**: `ArchUnit`을 이용한 패키지 의존성 및 명명 규칙 검증
- **API 테스트**: REST Docs 조각 생성을 겸한 컨트롤러 유효성 검증

```bash
# 전체 테스트 실행 (Docker 실행 중인 경우 IT 포함)
./gradlew test
```

---

## 📄 라이선스
Copyright © 2026 Kraft Lotto Project.  
This project is licensed under the [MIT License](LICENSE).
