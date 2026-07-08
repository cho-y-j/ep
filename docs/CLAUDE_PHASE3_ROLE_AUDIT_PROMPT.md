# Claude Code 지시 프롬프트 — Phase S-3 Role-Based UI + Audit Log Foundation

아래 내용을 Claude Code에 그대로 붙여넣어 사용한다.

```text
너는 skep-v2 프로젝트를 이어서 개발하는 코딩 에이전트다.

현재 프로젝트 위치:
C:\Users\dksej\Desktop\verify\skep-v2

현재 완료된 단계:
- Phase S-1: 현장/sites + 현장 참여업체/site_participants 기반 구현 완료.
- Phase S-2: 장비/인원 현장 배치 + 배치 이력 구현 완료.
- 이번 작업은 Phase S-3: Role-Based UI + Audit Log Foundation 이다.

먼저 반드시 아래 문서를 읽고 실제 코드와 맞는지 확인해라.
1. docs/WORK_PLAN_CENTERED_SYSTEM_DESIGN.md
2. docs/IMPLEMENTATION_LOG.md
3. docs/ROLE_BASED_DASHBOARD_DESIGN.md
4. docs/ERD.md
5. docs/API_SPEC.md
6. docs/LEGACY_SKEP_WORKSHEET_REFERENCE.md

이번 작업 목표:
역할별로 보여야 하는 대시보드, 메뉴, 기본 진입 라우트를 분리하고, 주요 업무 변경을 추적할 audit log 기반을 추가해라.

중요한 결정사항:
- ADMIN은 모든 회사/현장/장비/인원/서류/작업계획서 업무를 대신 처리할 수 있다.
- ADMIN은 강제 처리와 전체 로그/알림/서류 위험 관제가 가능해야 한다.
- BP는 자기 회사가 소유한 현장만 관리한다.
- BP는 현장에 장비공급사/인력공급사를 연결/해제할 수 있다.
- BP는 연결된 공급사의 장비/인원을 조회하고 현장에 배치/해제할 수 있다.
- BP는 공급사 장비/인원 자체 정보를 수정하지 않는다.
- BP는 공급사 서류를 업로드/갱신하지 않는다. 확인만 가능하다.
- BP는 서류 미비 자원을 강제 진행할 수 없다.
- 장비공급사는 자기 장비와 장비 서류를 관리한다.
- 장비공급사는 직접 현장 배치를 하지 않는다.
- 인력공급사는 자기 인원과 인원 서류를 관리한다.
- 인력공급사는 직접 현장 배치를 하지 않는다.
- 공급사는 연결된 BP 현장 정보와 BP가 만든 관련 작업계획서를 볼 수 있다.
- 작업계획서는 ADMIN 또는 BP가 만든다.
- 공급사는 작업계획서를 승인하지 않는다.
- WORKER/SITE_MANAGER는 아직 본격 구현하지 말고 확장 가능하게만 둔다.

Phase S-3에서 해야 할 일:

1. 현재 코드 구조 확인
- AuthContext, 라우팅, Sidebar, HomePage 또는 Dashboard 관련 파일 확인
- users.role, users.is_company_admin 사용 방식 확인
- Phase S-2 assignment API/UI가 실제로 붙어있는 위치 확인
- 기존 변경사항을 되돌리지 말고 현재 구조 위에 얹어라.

2. 역할별 기본 라우팅 분리
아래 경로를 기준으로 구현해라.

- /admin/dashboard
- /bp/dashboard
- /equipment-supplier/dashboard
- /manpower-supplier/dashboard
- /worker/dashboard 는 추후 placeholder 또는 redirect

기존 / 또는 /dashboard 가 있다면 로그인 사용자 role에 따라 위 경로로 redirect 하도록 만들어라.

3. 역할별 Dashboard Page 생성
가능하면 아래 파일 구조로 분리해라. 기존 프로젝트 구조에 더 맞는 위치가 있으면 그 패턴을 따른다.

- frontend/src/features/dashboard/AdminDashboardPage.tsx
- frontend/src/features/dashboard/BpDashboardPage.tsx
- frontend/src/features/dashboard/EquipmentSupplierDashboardPage.tsx
- frontend/src/features/dashboard/ManpowerSupplierDashboardPage.tsx
- frontend/src/features/dashboard/DashboardRedirect.tsx

더미 데이터만으로 화면을 완성하지 마라.
현재 실제 API로 가져올 수 있는 sites, assignments, equipment, persons, documents 정보를 우선 사용해라.
아직 API가 없는 항목은 빈 상태/준비중 상태로 두고 TODO를 명확히 남겨라.

4. Sidebar/Menu 권한 분리
역할별 메뉴를 분리해라.

ADMIN:
- 대시보드
- 회사 관리
- 사용자 승인/관리
- 현장 관리
- 장비 관리
- 인원 관리
- 서류 관리
- 작업계획서
- 알림
- 로그
- 설정

BP:
- 대시보드
- 현장 관리
- 참여 공급사
- 배치 장비
- 배치 인원
- 작업계획서
- 서류 위험
- 알림
- 로그

EQUIPMENT_SUPPLIER:
- 대시보드
- 내 장비
- 장비 서류
- 현장 관리
- 장비 배치 현황
- 작업 일정
- 알림
- 로그

MANPOWER_SUPPLIER:
- 대시보드
- 내 인원
- 인원 서류
- 현장 관리
- 인원 배치 현황
- 작업 일정
- 알림
- 로그

공급사의 "현장 관리"는 현장 생성/수정이 아니라 참여 현장 조회/관련 정보 확인이다.

5. Audit Log DB/API 기반 추가
새 마이그레이션을 추가해라.

권장 테이블:

audit_logs
- id
- actor_user_id
- actor_role
- actor_company_id
- action
- target_type
- target_id
- target_company_id
- site_id
- before_json
- after_json
- ip_address
- user_agent
- created_at

백엔드 코드:
- AuditLog 엔티티
- AuditLogRepository
- AuditLogService
- AuditLogController
- AuditLogResponse DTO

권장 API:
- GET /api/audit-logs
- GET /api/audit-logs/recent

권한:
- ADMIN은 전체 로그 조회 가능
- BP 회사 관리자는 자기 회사 현장 관련 로그 조회 가능
- 장비공급사 회사 관리자는 자기 회사 장비/서류/참여 현장 관련 로그 조회 가능
- 인력공급사 회사 관리자는 자기 회사 인원/서류/참여 현장 관련 로그 조회 가능
- 일반 직원은 우선 본인 로그만 보거나, 구현이 애매하면 관리자 권한으로 제한해라.

6. 주요 API에 audit log 기록 연결
이번 Phase에서 최소한 아래 액션은 로그를 남겨라.

- SITE_CREATED
- SITE_UPDATED
- PARTICIPANT_ADDED
- PARTICIPANT_REMOVED
- EQUIPMENT_ASSIGNED
- EQUIPMENT_UNASSIGNED
- PERSON_ASSIGNED
- PERSON_UNASSIGNED
- EQUIPMENT_STATUS_CHANGED, 이미 상태 변경 API가 있으면
- DOCUMENT_UPLOADED, 연결이 간단하면
- DOCUMENT_RENEWED, 연결이 간단하면

before_json/after_json 전체 캡처가 부담되면 최소 식별자와 변경 후 상태부터 기록하고 문서에 제한사항을 남겨라.

7. 역할별 대시보드 API 설계
이번에 전부 구현하지 못해도 API_SPEC에 방향을 남겨라.

권장:
- GET /api/dashboards/admin/summary
- GET /api/dashboards/bp/summary
- GET /api/dashboards/equipment-supplier/summary
- GET /api/dashboards/manpower-supplier/summary

role 기반 단일 /api/dashboard/summary보다 역할별 endpoint 분리를 우선 검토해라.
이유는 응답 스키마와 관심 데이터가 역할별로 달라질 가능성이 높기 때문이다.

8. 문서 갱신
작업 후 반드시 아래 문서를 갱신해라.

- docs/ROLE_BASED_DASHBOARD_DESIGN.md
- docs/IMPLEMENTATION_LOG.md
- docs/API_SPEC.md
- docs/ERD.md
- docs/WORK_PLAN_CENTERED_SYSTEM_DESIGN.md

문서에는 아래 내용을 반드시 포함해라.
- 추가된 라우트
- 역할별 메뉴
- 추가된 audit_logs 테이블
- audit log API 명세
- 어떤 액션에 로그를 붙였는지
- 구현한 대시보드 범위
- 아직 남은 대시보드/API/로그 범위
- 다음 단계

9. 검증
가능하면 아래를 실행해라.
- frontend: npm.cmd run typecheck
- frontend: npm.cmd run build
- backend: Maven/JDK가 있으면 mvn -DskipTests compile
- Docker 환경이 가능하면 docker compose build backend / frontend

주의사항:
- 기존 사용자 변경사항을 되돌리지 마라.
- git reset, git checkout으로 작업물을 날리지 마라.
- 더미데이터만으로 기능을 완성하지 마라.
- 기존 UI 톤을 유지해라. SaaS 대시보드, 화이트 + 블루, rounded card, shadow-sm.
- 작업계획서 전체 구현으로 넘어가지 마라.
- 서류 정책 전체 구현으로 넘어가지 마라.
- 먼저 역할별 정보 구조와 audit log 기반을 고정해라.
```

