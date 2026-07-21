-- V118: 서류 수집요청 다중 대상 — request(1) → target(N) → item(M).
-- 요청 1건 = 토큰/링크 1개, 그 아래 대상 N개(장비+인력 혼합), 대상마다 서류 M개.
-- 수집된 서류는 병합하지 않고 항목별 개별 Document 로 등록(만료·교체·재검증이 서류 단위로 돌아야 함).
-- created_at: 코드베이스 전역 관례대로 TIMESTAMP (ddl-auto=validate + LocalDateTime 정합).

CREATE TABLE document_collection_target (
    id         BIGSERIAL PRIMARY KEY,
    request_id BIGINT      NOT NULL REFERENCES document_collection_request (id) ON DELETE CASCADE,
    owner_type VARCHAR(16) NOT NULL,          -- EQUIPMENT / PERSON
    owner_id   BIGINT      NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE (request_id, owner_type, owner_id)
);
CREATE INDEX idx_doc_collect_target_req ON document_collection_target (request_id, sort_order);
CREATE INDEX idx_doc_collect_target_owner ON document_collection_target (owner_type, owner_id);

-- 기존 요청 = 대상 1건짜리 요청으로 이관.
INSERT INTO document_collection_target (request_id, owner_type, owner_id, sort_order)
SELECT id, owner_type, owner_id, 0 FROM document_collection_request;

-- 기존 item → target 매핑. 이관 시점엔 request 당 target 이 1개뿐이라 request_id 로 유일하게 결정된다.
ALTER TABLE document_collection_item ADD COLUMN target_id BIGINT;
UPDATE document_collection_item i
   SET target_id = t.id
  FROM document_collection_target t
 WHERE t.request_id = i.request_id;
ALTER TABLE document_collection_item ALTER COLUMN target_id SET NOT NULL;
ALTER TABLE document_collection_item
    ADD CONSTRAINT document_collection_item_target_fkey
    FOREIGN KEY (target_id) REFERENCES document_collection_target (id) ON DELETE CASCADE;

-- 기존 UNIQUE(request_id, document_type_id) 제거 — 한 요청에 크레인 2대가 같은 서류를 각각 내야 한다.
-- V75 에서 이름 없이 선언돼 이름이 자동생성이라 컬럼 조합으로 찾아 DROP.
DO $$
DECLARE cname TEXT;
BEGIN
    FOR cname IN
        SELECT c.conname
          FROM pg_constraint c
         WHERE c.conrelid = 'document_collection_item'::regclass
           AND c.contype = 'u'
           AND (SELECT array_agg(a.attname::text ORDER BY a.attname::text)
                  FROM pg_attribute a
                 WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey))
               = ARRAY['document_type_id', 'request_id']
    LOOP
        EXECUTE 'ALTER TABLE document_collection_item DROP CONSTRAINT ' || quote_ident(cname);
    END LOOP;
END $$;

ALTER TABLE document_collection_item
    ADD CONSTRAINT document_collection_item_target_type_key UNIQUE (target_id, document_type_id);
CREATE INDEX idx_doc_collect_item_target ON document_collection_item (target_id, sort_order);

-- 대상은 이제 target 테이블이 갖는다. (idx_doc_collect_owner 는 컬럼 삭제와 함께 자동 제거)
ALTER TABLE document_collection_request DROP COLUMN owner_type;
ALTER TABLE document_collection_request DROP COLUMN owner_id;
