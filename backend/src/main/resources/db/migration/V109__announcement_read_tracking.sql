-- P4a 공지 확인 추적 — 기존 /api/announcements/broadcast 는 fire-and-forget FCM(영속·읽음 없음).
-- 발송 공지를 영속화하고 수신자(작업자)별 확인(읽음) 시각을 기록 → 발송자에게 확인율·미확인자 명단.
-- 안전 상황판 [공지] 탭에서 확인 현황을 한눈에.

CREATE TABLE announcements (
    id                BIGSERIAL PRIMARY KEY,
    site_id           BIGINT,                 -- 현장 스코프(선택). NULL=현장 미지정(전역).
    title             VARCHAR(150) NOT NULL,
    body              TEXT         NOT NULL,
    sender_user_id    BIGINT,                 -- 발송한 관리자(users.id).
    sender_company_id BIGINT,                 -- 발송자 회사(BP 스코프).
    target            VARCHAR(16),            -- phone | all | watch (발송 기기 대상).
    created_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_announcements_sender_company ON announcements (sender_company_id);
CREATE INDEX idx_announcements_site ON announcements (site_id);

CREATE TABLE announcement_recipients (
    id              BIGSERIAL PRIMARY KEY,
    announcement_id BIGINT    NOT NULL REFERENCES announcements (id) ON DELETE CASCADE,
    person_id       BIGINT    NOT NULL,       -- 수신 작업자(persons.id).
    read_at         TIMESTAMP,                -- 확인(읽음) 시각. NULL=미확인.
    CONSTRAINT uq_announcement_recipient UNIQUE (announcement_id, person_id)
);

CREATE INDEX idx_announcement_recipients_person ON announcement_recipients (person_id);
