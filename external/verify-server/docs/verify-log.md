# Verify System 작업일지

## 2026-01-15

### 변경 요약
- KOSHA 검증 플로우에 OCR 단계 분리
- QR 추출 실패 시 OCR → KOSHA 직접 조회 fallback 유지
- strict-mode=false 기본값 유지

### 추가된 컴포넌트
- QRCodeService (ZXing, multi-region)
- OCRService (Google Vision / Stub 분기)
- KoshaVerificationService

### 결정 이유
- QR 미포함 이수증 대응 필요
- OCR 정확도 편차로 strict-mode 기본 비활성화
- 외부 API 장애 시 전체 시스템 fail-closed 유지

### 판정 영향
- OCR 실패 + KOSHA 성공 → VALID (strict=false)
- OCR 실패 + KOSHA 실패 → INVALID

### 리스크 / 주의
- KOSHA HTML 구조 변경 시 파싱 로직 영향
- Google Vision API quota 초과 가능성