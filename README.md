# SKEP v2

장비투입관리 플랫폼 — 모놀리스 rewrite.

## 구조

```
skep-v2/
├── backend/          Spring Boot 3.2 (Java 17)
├── frontend/         Vite 7 + React 19 + Tailwind v4
├── infrastructure/   배포 스크립트
├── .github/workflows CI/CD
└── docker-compose.yml
```

## 로컬 실행

```bash
cp .env.example .env
docker-compose up -d
# backend: http://localhost:8081/api/health
# frontend: http://localhost:8082
```

개발 모드 (hot reload):

```bash
# 터미널 1
cd backend && mvn spring-boot:run

# 터미널 2
cd frontend && npm install && npm run dev
# http://localhost:5173 (vite proxy → backend 8080)
```

## 진행 현황

기능은 하나씩 설계하며 추가합니다. 현재는 부팅 가능한 스캐폴드 + CI/CD 파이프라인만 깔린 상태.

- [x] Phase 0: 스캐폴드 + CI/CD
- [ ] Phase 1: 인증 (User + JWT + Spring Security)
- [ ] Phase 2: Company / Person / Equipment / Document
- [ ] Phase 3: 만료 추적 + OCR (verify-api 연동)
- [ ] Phase 4: 작업계획서 (DOCX + OnlyOffice)
- [ ] Phase 5: 현장/배차/점검/정산/알림

상세: `../skep/REWRITE_GUIDE.md`

## 배포

`main` 브랜치 push → GitHub Actions Deploy 워크플로 → SSH로 서버에서 git pull + docker-compose build/up.

필요한 GitHub Secrets:
- `SSH_HOST` — 배포 서버 IP
- `SSH_USER` — SSH 유저
- `SSH_PRIVATE_KEY` — 개인키 (Ed25519 권장)
- `REMOTE_PATH` — 서버상의 프로젝트 경로 (예: `/home/ec2-user/skep-v2`)
