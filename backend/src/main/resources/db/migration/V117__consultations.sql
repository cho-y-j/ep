-- V117: 상담 요청(Consultation) — 공개 랜딩에서 비로그인 방문자가 남기는 도입 문의.
-- 저장 즉시 ADMIN 에게 시스템 알림 1건. ADMIN 이 목록 확인 후 handled 처리.
-- created_at: 코드베이스 전역 관례대로 TIMESTAMP (ddl-auto=validate + LocalDateTime 정합).
CREATE TABLE consultations (
    id           BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(255) NOT NULL,
    phone        VARCHAR(64)  NOT NULL,
    email        VARCHAR(255),
    message      TEXT,
    handled      BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_consultations_created ON consultations (created_at DESC);
