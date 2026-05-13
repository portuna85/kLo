# 🎰 Kraft Lotto

> **Spring Boot 4 기반의 로또 추천 서비스**  
> 추천 번호 생성 · 당첨번호 조회/관리 · 통계 분석 · REST Docs 자동 문서화

---

## ✨ 주요 기능

| 기능 | 엔드포인트 |
|------|-----------|
| 🎯 추천 번호 생성 | `POST /api/recommend` |
| 📋 당첨번호 조회 | `/api/winning-numbers/**` |
| 📊 번호별 출현 빈도/요약 통계 | `/api/winning-numbers/stats/**` |
| 🔧 관리자 수동 수집/갱신 | `/admin/lotto/**` |
| 💚 Actuator 헬스체크 | `/actuator/health/readiness` |
| 📖 REST Docs 문서 | `/docs/index.html` |

---

## 🛠️ 기술 스택

```
Language   : Java 25
Framework  : Spring Boot 4.0.5
Web/보안   : Spring Web · Validation · Security · Actuator
데이터     : Spring Data JPA · Flyway · MariaDB
캐시       : Caffeine Cache · (선택) Redis Rate Limit
추적       : Micrometer + OTLP Tracing
프론트엔드 : Thymeleaf + Bootstrap (WebJars)
테스트     : JUnit 5 · REST Docs · Testcontainers · ArchUnit
```

---

## 🚀 빠른 시작

### 1단계 — 환경 변수 파일 복사

```bash
cp .env.example .env
```

### 2단계 — 필수 값 설정

`.env` 파일을 열고 아래 세 항목을 반드시 채우세요.

```dotenv
KRAFT_DB_PASSWORD=<DB 비밀번호>
KRAFT_DB_ROOT_PASSWORD=<루트 비밀번호>
KRAFT_ADMIN_API_TOKENS=<관리자 API 토큰>
```

> ⚠️ **이 세 값이 없으면 `prod` 프로파일에서 애플리케이션이 기동되지 않습니다.**

### 3단계 — 컨테이너 실행

```bash
docker compose up -d --build
```

### 4단계 — 동작 확인

```
앱     →  http://localhost:8080/
헬스   →  http://localhost:8080/actuator/health/readiness
문서   →  http://localhost:8080/docs/index.html
```

---

## 💻 로컬 실행

```bash
# macOS / Linux
./gradlew bootRun

# Windows
.\gradlew.bat bootRun
```

---

## 🔨 빌드 · 테스트 · 문서화

```bash
# 전체 빌드
./gradlew clean build

# 통합 테스트
./gradlew integrationTest

# REST Docs 생성
./gradlew asciidoctor

# 문서 포함 JAR 패키징
./gradlew bootJarWithDocs
```

---

## 📡 API 레퍼런스

### Public API

```http
POST   /api/recommend
GET    /api/recommend/rules

GET    /api/winning-numbers/latest
GET    /api/winning-numbers/{round}
GET    /api/winning-numbers?page={page}&size={size}

GET    /api/winning-numbers/stats/frequency
GET    /api/winning-numbers/stats/frequency-summary
```

### Admin API

```http
POST   /api/winning-numbers/refresh                    # (레거시)
POST   /admin/lotto/draws/collect-next
POST   /admin/lotto/draws/collect-missing
POST   /admin/lotto/draws/{drwNo}/refresh
POST   /admin/lotto/draws/backfill?from=...&to=...
POST   /admin/lotto/jobs/backfill?from=...&to=...
GET    /admin/lotto/jobs/{jobId}
```

Admin 요청 시 헤더에 토큰을 포함해야 합니다.

```http
X-Kraft-Admin-Token: <token>
```

---

## ⚙️ CI/CD 파이프라인

```
CI  (ci.yml)
 ├── 테스트 실행
 ├── 빌드
 ├── JAR 생성
 └── build/generated-snippets → artifact 업로드

CD  (workflow_run 트리거)
 ├── CI artifact에서 snippets 다운로드
 ├── Docker 이미지 빌드
 ├── 서버 배포
 └── (수동 실행 시) fallback: ./gradlew test → snippets 재생성
```

---

## 🧰 유용한 명령어

**필수 환경 변수 목록 확인**

```bash
./gradlew -q printRequiredEnvVars
```

**JAR 리소스 검증 (prod 프로파일 포함 여부)**

```powershell
pwsh ./scripts/verify-prod-profile-in-jar.ps1 -JarPath build/libs/app-with-docs.jar
```

---

## 📄 라이선스

MIT License — 자세한 내용은 [`LICENSE`](LICENSE) 파일을 참고하세요.
