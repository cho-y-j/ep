-- V34: 공급사 → BP 영업 견적 발송.
-- 공급사가 자기 자원 + 단가 + 수신자(등록 BP 또는 외부 이메일) 입력 → PDF + 메일/알림 발송.
-- BP는 수신함에서 조회만 (수락/거절 없음, 자료 수준).

CREATE TABLE outgoing_quotations (
    id                      BIGSERIAL PRIMARY KEY,
    supplier_company_id     BIGINT       NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    sent_by_user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    -- 자원 (장비 OR 인원)
    equipment_id            BIGINT       REFERENCES equipment(id) ON DELETE SET NULL,
    person_id               BIGINT       REFERENCES persons(id) ON DELETE SET NULL,

    -- 단가/메모
    daily_rate              INTEGER,
    monthly_rate            INTEGER,
    note                    TEXT,

    -- 작업 기간 (옵션 — 공급사가 가능 기간 명시)
    period_start            DATE,
    period_end              DATE,

    -- 수신자: REGISTERED_BP (등록된 BP) 또는 EMAIL (외부 이메일)
    recipient_type          VARCHAR(16)  NOT NULL,
    recipient_user_id       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    recipient_company_id    BIGINT       REFERENCES companies(id) ON DELETE SET NULL,
    recipient_email         VARCHAR(255),

    -- 발송 결과
    pdf_size                INTEGER,
    sent_at                 TIMESTAMP    NOT NULL DEFAULT NOW(),
    mail_sent               BOOLEAN      NOT NULL DEFAULT FALSE,
    mail_error              TEXT,

    created_at              TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outq_supplier      ON outgoing_quotations (supplier_company_id);
CREATE INDEX idx_outq_recipient_user ON outgoing_quotations (recipient_user_id);
CREATE INDEX idx_outq_recipient_comp ON outgoing_quotations (recipient_company_id);
CREATE INDEX idx_outq_sent_at        ON outgoing_quotations (sent_at DESC);
