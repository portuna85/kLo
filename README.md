# 🎲 KraftLotto (kLo)

> **편향 회피형 로또 6/45 추천 서비스**
>
> 당첨 확률을 높인다고 주장하지 않습니다.
> 과거 패턴 기반의 비합리적 조합을 줄이는 데 목적이 있습니다.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![MariaDB](https://img.shields.io/badge/MariaDB-11.8-003545?style=flat-square&logo=mariadb&logoColor=white)](https://mariadb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)

---

## 🏗️ 아키텍처

```text
com.kraft.lotto
│
├─ feature
│  ├─ recommend ─────────── 추천 로직
│  │  ├─ domain
│  │  ├─ application
│  │  └─ web
│  │
│  └─ winningnumber ─────── 당첨번호 관리
│     ├─ domain
│     ├─ application
│     ├─ infrastructure
│     ├─ event
│     └─ web
│
├─ infra
│  ├─ config ────────────── 공통 설정
│  └─ security ──────────── 보안 설정
│
└─ support ──────────────── 공통 유틸 · 예외
```

---

## ⚡ 빠른 시작

### 1 — 환경 파일 생성

```powershell
cd D:\workspace\spring
Copy-Item .env.example .env
```

### 2 — 컨테이너 실행

```powershell
cd D:\workspace\spring
docker compose up -d --build
```

### 3 — 접속 확인

| 엔드포인트 | URL |
|:---|:---|
| Web | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |
| API Docs | `http://localhost:8080/docs/index.html` |

---

## 🖥️ 로컬 실행

```powershell
cd D:\workspace\spring
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

---

## 📡 주요 API

### 추천 — 번호 조합 생성

```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{"count":5}'
```

### 당첨번호 — 조회

```bash
# 최신 회차
curl http://localhost:8080/api/winning-numbers/latest

# 특정 회차
curl http://localhost:8080/api/winning-numbers/1100

# 페이징 조회
curl "http://localhost:8080/api/winning-numbers?page=0&size=20"

# 번호 출현 빈도
curl http://localhost:8080/api/winning-numbers/stats/frequency
```

### 관리자 — 수집 트리거

```bash
curl -u "$KRAFT_ADMIN_USERNAME:$KRAFT_ADMIN_PASSWORD" \
  -X POST http://localhost:8080/api/admin/winning-numbers/refresh \
  -H "Content-Type: application/json" \
  -d '{"targetRound":"1103"}'
```

---

## 🔒 보안 운영 메모

> **Nginx** — `set_real_ip_from`으로 신뢰 프록시 구성
>
> **Application** — `server.forward-headers-strategy=NATIVE`
>
> **Admin API** — Basic Auth **+** IP 화이트리스트 이중 보호

---

## 🔑 필수 환경 변수

| 변수 | 설명 |
|:---|:---|
| `KRAFT_DB_URL` | DB 접속 URL |
| `KRAFT_DB_USER` | DB 사용자 |
| `KRAFT_DB_PASSWORD` | DB 비밀번호 |
| `KRAFT_ADMIN_USERNAME` | 관리자 계정 |
| `KRAFT_ADMIN_PASSWORD` | 관리자 비밀번호 |
| `KRAFT_ADMIN_ALLOWED_IP_RANGES` | 허용 IP (콤마 구분 CIDR) |

```dotenv
# 예시
KRAFT_ADMIN_ALLOWED_IP_RANGES=127.0.0.1/32,::1/128,192.168.0.0/24
```

---

## 🧪 테스트

```powershell
cd D:\workspace\spring
.\gradlew.bat test
```

아키텍처 규칙 · 보안 · 웹 계층 테스트가 포함됩니다.

---

## 🛠️ 기술 스택

| 영역 | 기술 |
|:---|:---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle 9 (Kotlin DSL) |
| Database | MariaDB 11.8 LTS |
| Template | Thymeleaf |
| Container | Docker Compose |
| Architecture Test | ArchUnit |

---

> Built with ☕ by [portuna85](https://github.com/portuna85)