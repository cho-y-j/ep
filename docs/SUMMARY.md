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
- **미완/보류**: 자동화 C(수락=배치, 의도 확인 필요), A-2(TARGETED 초안), UX 미결 6문, 나머지 자동화 B/F/G/H/I. 워치 낙상감지 on-device 실검증은 사용자 몫.
- **커밋/푸시 안 함**(사용자 요청 대기). 작업트리에 이번+이전 세션 미커밋 변경 누적.

## 주요 구성요소
- **backend**: com.skep.{quotation(견적·제안·배차·초안), settlement, site, company, workconfirmation, safety, fieldDeployment, ...}. Flyway 최신 = **V80**. Hibernate ddl-auto=validate. JSON 전역 SNAKE_CASE.
- **frontend**: features/{quotation, settlement, site, dashboard, fieldDeployment, ...}. 공용 AppShell·CollapsibleSection.
- **app**: watch-app(Wear OS 낙상감지), 모바일 앱.
- **infra**: docker-compose(postgres/redis/onlyoffice). dev-local/ 스크립트(backend.sh·frontend.sh).

## 최근 변경 이력
| 날짜 | 세션 요약 | 상세 |
|------|-----------|------|
| 2026-07-11 | 감사 수정(500→400·N+1) + UX P0 + 자동화 E/D/A, 실데이터 독립검증 | docs/details/2026-07-11-audit-remediation-automation.md |
| 2026-07-10 | 정산 재설계(÷25·근무일수·현장정산일 V79), 협력사 설계, 워치 낙상감지 | docs/details/2026-07-10-*.md |

## 참고 문서
- `docs/details/` — 세션·주제별 상세 기록
- `docs/TODO.md` — 미완료/후속 작업
- `docs/API_SPEC.md` · `docs/ERD.md` · `docs/CORE_BUSINESS_RULES.md` — 기존 명세
