# Contributing

## Branch/PR

- 기본 브랜치: `main`
- 브랜치 이름: `feature/<topic>`, `fix/<topic>`, `docs/<topic>`
- PR 제목: `[type] summary` (`type`: feat/fix/docs/chore/refactor)

## Development

```bash
docker compose up -d --build
./gradlew build
```

## Commit

- 한 커밋에는 하나의 논리 변경만 포함
- 설정/보안 변경 시 `.env.example`와 문서 동기화

## Review Checklist

- 기능 변경 시 API/문서 영향 확인
- 운영 영향(배포, 롤백, 마이그레이션) 확인
- 보안 헤더/토큰/민감정보 로그 노출 점검
