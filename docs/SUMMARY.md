# 프로젝트 요약 (SUMMARY)

> `/wrap` 슬래시 커맨드가 세션 작업을 반영해 갱신하는 **누적 상태 요약** 문서입니다.
> 상세 근거는 `docs/details/`, 할 일은 `docs/TODO.md` 를 참조하세요.

## 개요
SKEP v2 — BP(발주)·장비공급사·인력공급사·협력사·작업자를 잇는 장비/인력 투입관리 플랫폼(모놀리스). Spring Boot 3.2(Java17)+Flyway, Vite7/React19/Tailwind4. 로컬 dev: 백엔드 8091(dev 프로필)·프론트 5185·docker postgres 15433.

## 현재 상태 (2026-07-11)
- **정산 재설계 완료·독립검증**: 월대=(월대÷25)×근무일수+OT, 일대=일대×근무일수+OT. 근무일수·OT는 투입 시점 확정, 현장별 정산일, 날짜범위 조회. `SettlementCalculator`에 격리.
- **5역할 기능·성능 감사 완료**: 로그인·핵심기능·권한경계·크로스테넌트 격리 전부 정상(보안 우회 없음). 경미 결함은 아래 처리.
- **감사 수정 완료**: 클라이언트 오류 500→400, 견적/안전알림 직렬화 N+1 배치화(출력 불변).
- **UX P0 완료**: 거짓 안내문 3곳 수정, 견적 후보 자동조회, BP 대시보드 처리대기 위젯.
- **자동화 3종 완료·독립검증(실데이터, 0 결함)**: E 견적 후보 배지, D 정산 근무일수 자동파생(수동 우선), A 선정→배차 초안(V80 별도테이블 격리).
- **safe-8 자동화 완료·검증**: 서명대기 집계·OT 프리필·견적 단계칩·일괄수락·다이얼로그 통일·만료 알림 스케줄러·공급사 위젯 정합(+E/D/A). 독립 QA 부분검증(#7 스케줄러는 cron 미트리거로 로직만).
- **캡스톤 완료**: 4주체 기능테스트(협력사 V77 실동작 확인) + 전문가 UX·자동화 전략 → `docs/details/2026-07-11-ux-automation-strategy.md`.
- **투입 파이프라인 스펙 확정**: `docs/details/deployment-pipeline-spec.md`(서류→검사→투입대기→투입 정산율확정→작업확인서→거래내역서).
- **협력사 A/B/C/D 완료·QA 검증**: A 대행 전체수정(격리 유지) / B 자가로그인+부모승인 / C 투입대기 readiness(`GET /api/resources/readiness`, 게이트 미러링) / D 거래내역서(`GET /api/settlements/statement` PDF+Excel, 정산 숫자 그대로). 앞 캡스톤의 협력사 갭 2건 해소됨.
- **주체별 UX 검토 완료**: `docs/details/2026-07-11-per-role-ux-review.md`.
- **커밋+푸시 완료**: `fda8556`을 `cho-y-j/ep`(공개)에 반영(사용자 실행). **단, 그 이후 safe-8(R1 backend+R2 frontend) 변경은 미커밋** — 다음 커밋 대상.
- **미완/보류**: §7 결정 6건(협력사 2 + C/A-2/B/서류허브·네비). 워치 낙상감지 on-device 실검증은 사용자 몫.

## 주요 구성요소
- **backend**: com.skep.{quotation(견적·제안·배차·초안), settlement, site, company, workconfirmation, safety, fieldDeployment, ...}. Flyway 최신 = **V80**. Hibernate ddl-auto=validate. JSON 전역 SNAKE_CASE.
- **frontend**: features/{quotation, settlement, site, dashboard, fieldDeployment, ...}. 공용 AppShell·CollapsibleSection.
- **app**: watch-app(Wear OS 낙상감지), 모바일 앱.
- **infra**: docker-compose(postgres/redis/onlyoffice). dev-local/ 스크립트(backend.sh·frontend.sh).

## 최근 변경 이력
| 날짜 | 세션 요약 | 상세 |
|------|-----------|------|
| 2026-07-11 | 인력 OT(V81)·자원 파이프라인 보드·BP 네비 4그룹·서류허브 + 종합보고(사용자중심 UX), QA 검증, 커밋 9589c09 | docs/details/2026-07-11-final-report.md |
| 2026-07-11 | 협력사 A/B/C/D(대행수정·자가로그인승인·투입대기·거래내역서) + 주체별 UX 검토, 독립 QA 검증 | docs/details/2026-07-11-per-role-ux-review.md, deployment-pipeline-spec.md |
| 2026-07-11 | safe-8 자동화 + 캡스톤(4주체 기능테스트·UX/자동화 전략) | docs/details/2026-07-11-ux-automation-strategy.md |
| 2026-07-11 | 감사 수정(500→400·N+1) + UX P0 + 자동화 E/D/A, 실데이터 독립검증 | docs/details/2026-07-11-audit-remediation-automation.md |
| 2026-07-10 | 정산 재설계(÷25·근무일수·현장정산일 V79), 협력사 설계, 워치 낙상감지 | docs/details/2026-07-10-*.md |

## 참고 문서
- `docs/details/` — 세션·주제별 상세 기록
- `docs/TODO.md` — 미완료/후속 작업
- `docs/API_SPEC.md` · `docs/ERD.md` · `docs/CORE_BUSINESS_RULES.md` — 기존 명세
