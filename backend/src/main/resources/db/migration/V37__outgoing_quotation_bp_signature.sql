-- V37: 정식 견적서에 BP 수락 사인 추가
-- 공급사 발송 → BP 수신 → BP가 수락 의사로 캔버스 사인 → DB에 PNG 저장.
-- 공급사와 BP 양쪽 수신함에서 사인된 견적서 표시.

ALTER TABLE outgoing_quotations
    ADD COLUMN bp_signature_png   BYTEA,
    ADD COLUMN bp_signed_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN bp_signer_name     VARCHAR(100),
    ADD COLUMN bp_signed_at       TIMESTAMP;
