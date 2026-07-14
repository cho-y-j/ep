# 로컬 OCR 검증 — prod 배포/확인 항목 (2026-07-14)

> 로컬 영역-추출→진위검증 배선은 완료(VerificationService Vision-skip 가드). 아래는 **prod에서만 확정/테스트 가능**한 항목.

## 1. 정부API 실호출 = prod 전용
dev는 `VERIFY_ENABLED=false`(dev-local/backend.sh) → `VerifyClient`가 `UPSTREAM_DISABLED` 반환, RIMS/CARGO/NTS 실호출 안 됨. **검증 흐름(로컬추출 필드→정부API)은 prod(VERIFY_ENABLED=true + 자격증명)에서 검증.** 가드 자체는 컴파일·재기동으로 활성됨.

## 2. RIMS 면허종별코드 (라벨→2자리 코드) — 확인 필요
- RIMS `f_licn_con_code`는 `\d{2}` 강제(`main-api RimsLicenseVerifyRequest`). region template은 코드를 추출 못 하고 FE 칩은 **한글 라벨** 저장 → 그대로면 400.
- **코드표 상충(확인 필요)**: verify-api `parseLicense`는 2종=32/33/38, main-api Schema는 21/22/23. **실제 RIMS 규격 확인 후 확정.**
- **변경 위치**: FE `OcrUploadDialog` `LicenseTypeChips`를 `{라벨:코드}`로 바꿔 `manualLicenseConditionCode`에 **코드** 저장(BE 무변경). 또는 BE VerificationService에서 라벨→코드 매핑.
- **주의**: 이 gap은 현 Vision 경로에도 존재(선재). RIMS가 면허번호+성명만으로 판정하고 코드는 형식만 맞으면 되는지도 확인.

## 3. KOSHA(안전교육) QR 검증 — 설정만
- verify-api가 **이미 ZXing으로 QR 추출 + KOSHA 포털 조회**(코드 무변경). OCR(Vision)은 보조.
- **Vision-free 조건**: verify-api `google.vision.api-key` **미설정**(→StubOCR) + `verify.strict-mode=false`(default) → 포털조회 성공만으로 VALID.
- 현 dev verify-api 컨테이너엔 `GOOGLE_VISION_API_KEY`가 설정돼 있음 → **Vision 완전 0 원하면 그 키 제거** + strict-mode 확인.

## 4. 이중 verify 호출
FE `doUpload`의 명시적 `/{id}/verify` + `AutoVerifyTrigger`(AFTER_COMMIT) 둘 다 발화 → prod에서 RIMS/CARGO **2회 호출**. 유료/쿼터 API면 하나로 정리 검토(기존 동작).

## 5. 사업자등록증(NTS_BIZ)
region template 미보유 → 가드상 여전히 Vision `extractOcr(BUSINESS)` 사용. 로컬화하려면 Phase3 도구로 사업자등록증 template 추가.

## 6. PiiMasker 로컬화
주민번호 마스킹의 Vision 의존 → 로컬 paddle `/mask-pii`로 대체(별도 작업). 완료 시 Vision 완전 0.
