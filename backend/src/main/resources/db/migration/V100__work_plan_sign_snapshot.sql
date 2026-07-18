-- P1a 기반②: 서명 실문서화 스냅샷.
-- 작성자 서명/외부 서명요청 시점에 클라이언트가 현재 formValues 로 렌더한 PDF 를 저장해 둔 FileStorage key.
-- 공개 /sign/{token}/pdf 와 서명요청 메일 첨부가 이 스냅샷을 우선 서빙(없으면 기존 셸 렌더 폴백).
-- 내용 변경(updateFormValues·자원 add/remove) 시 서명 전체 무효화와 함께 key 를 clear.
ALTER TABLE work_plans ADD COLUMN sign_snapshot_key VARCHAR(255);
