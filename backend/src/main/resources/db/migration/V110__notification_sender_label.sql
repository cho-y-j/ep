-- P4d: 알림 발신자 표시. "누가 보냈는지" 표시용 라벨(비정규화).
-- 예: "시스템 (강풍 경보)" / "테스트 BP건설(주) 김소장" / "관리자".
-- 기존 행은 NULL → 화면에서 "—"(미상) 정직 표기. 신규 발송부터 발송 지점이 라벨을 기록.
ALTER TABLE notifications
    ADD COLUMN sender_label VARCHAR(120);
