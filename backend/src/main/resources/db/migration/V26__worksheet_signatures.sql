-- V26: 작업계획서 첫 페이지 5개 사인란 전자서명 워크플로우
--
-- 5개 역할:
--   AUTHOR (작성자 = Biz.P 현장소장) — BP 본인이 로그인 상태로 직접 사인
--   SUPERVISOR (담당자 = SKEP 관리감독자) — 이메일 요청 사인
--   CONFIRMER (확인자 = SKEP HYPER) — 이메일 요청 사인
--   REVIEWER (검토자 = SKEP 안전관리자) — 이메일 요청 사인
--   APPROVER (승인자 = SKEP 현장총괄) — 이메일 요청 사인
--
-- 상태: PENDING(생성됨, 사인 대기) / SIGNED / EXPIRED(만료) / INVALIDATED(워크시트 수정으로 무효화)
-- token_expires_at: 사인 링크 7일 만료
-- signature_png: 캔버스 PNG (BYTEA)

CREATE TABLE worksheet_signatures (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('AUTHOR', 'SUPERVISOR', 'CONFIRMER', 'REVIEWER', 'APPROVER')),
    signer_name VARCHAR(100),
    signer_email VARCHAR(255),
    sign_token VARCHAR(64) UNIQUE,
    token_expires_at TIMESTAMP,
    signature_png BYTEA,
    signed_at TIMESTAMP,
    signed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SIGNED','EXPIRED','INVALIDATED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (work_plan_id, role)
);

CREATE INDEX idx_worksheet_signatures_token ON worksheet_signatures(sign_token) WHERE sign_token IS NOT NULL;
CREATE INDEX idx_worksheet_signatures_work_plan ON worksheet_signatures(work_plan_id);
CREATE INDEX idx_worksheet_signatures_status ON worksheet_signatures(status);
