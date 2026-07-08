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

Phase 0~4 완료. Phase 5는 현장/배차/알림/안전점검까지 구현, 정산은 미구현. 이후 S-시리즈(S-1~S-11) + Phase Bid로 견적·공개입찰·보완요청까지 확장됨. DB 마이그레이션 V1~V46 (V29 결번).

- [x] Phase 0: 스캐폴드 + CI/CD
- [x] Phase 1: 인증 (User + JWT + Spring Security)
- [x] Phase 2: Company / Person / Equipment / Document
- [x] Phase 3: 만료 추적 + OCR (verify-api 연동) — 만료 알림 스케줄러(cron)는 미구현
- [x] Phase 4: 작업계획서 (DOCX + OnlyOffice)
- [~] Phase 5: 현장 / 배차 / 알림 / 안전점검 구현 · **정산(settlement) 미구현** · 장비 점검이력은 시드 데이터만

확장 (상세는 `docs/IMPLEMENTATION_LOG.md`):

- [x] 견적 요청·응답·최종선정(TARGETED) / 공개입찰(OPEN_BID) / 배차 / 견적서 xlsx·pdf / 비교 스냅샷
- [x] 영업견적(outgoing) / 원청기관(client-org)+투입이력 / 서류 보완요청 / 컴플라이언스 점검표 / compliance-orders(V46)
- [x] 작업확인서 / 전자서명(이메일 토큰) / OnlyOffice 인플레이스 편집
- [ ] 정산 / 만료 알림 cron / DOCX 표 행 반복 / AI 재작성(현재 stub)

> 참고: 견적 최종선정(finalize)은 target을 FINAL_ACCEPTED로 바꿀 뿐 작업계획서를 자동 생성하지 않는다 — 작업계획서는 BP가 별도로 작성한다.

문서: API 명세 `docs/API_SPEC.md` · 데이터 모델 `docs/ERD.md` · 비즈니스 규칙 `docs/CORE_BUSINESS_RULES.md` · rewrite 가이드 `../skep/REWRITE_GUIDE.md`

## 배포

`main` 브랜치 push → GitHub Actions Deploy 워크플로 → SSH로 서버에서 git pull + docker-compose build/up.

필요한 GitHub Secrets:
- `SSH_HOST` — 배포 서버 IP
- `SSH_USER` — SSH 유저
- `SSH_PRIVATE_KEY` — 개인키 (Ed25519 권장)
- `REMOTE_PATH` — 서버상의 프로젝트 경로 (예: `/home/ec2-user/skep-v2`)
