# 장비등록 등록증 정합화 + 맞춘이미지 미리보기 (2026-07-14)

사용자 브라우저 테스트에서 발견된 2건을 수정. 배경: 로컬 영역-템플릿 OCR(Phase1·2) 위에서 동작.

## 1. 버그 — 4모서리 정렬 후 "맞춘 이미지"·추출이 안 뜸
**증상**: 장비공급사 로그인 → 장비 추가 → 이미지 등록증 4모서리 맞춤 → warp 결과 미리보기 없음, 추출도 안 보임.

**원인**:
- `OcrRegionPreviewController`가 `{ok, fields}`만 반환 → warp 이미지가 BE 응답에 없음.
- `EquipmentCreateForm.onAlignConfirm`이 필드만 세팅, warp 이미지를 렌더 안 함.
- (추출은 실제로 호출됐음 — 로그상 `/ocr-region-preview` 발화. "안 됨"이 아니라 결과가 안 보였을 뿐.)

**수정**:
- BE: `OcrRegionPreviewController.preview` — corners 있으면 `PaddleOcrClient.extractRegionsRaw(..., returnWarped=true)`로 `warped_image_base64`까지 받아 응답에 포함(파일크기 응답은 WebClient 32MB 버퍼로 커버). corners 없으면 기존 `extractRegions`(flat) 그대로. 필드 파싱은 `parseFields(JsonNode)` 헬퍼.
- FE: `EquipmentCreateForm` — `warpedPreviewUrl` state 추가, `onAlignConfirm`이 응답의 `warped_image_base64`를 `data:image/png;base64,`로 미리보기에 표시("맞춘 이미지 (자동 크롭·원근보정)"). 새 파일 선택 시 초기화.
- paddle warp 인코딩 = PNG(`cv2.imencode('.png')`).

**검증**: 인증 API(`equipment1`)로 자동차등록증(id7) 3.9MB·건설기계등록증(id32) 2.4MB warp 반환 확인.

## 2. 폼이 장비종류 무관하게 자동차등록증만 요구
**증상**: 장비 종류(굴삭기 등) 선택도 하기 전에 폼이 무조건 "자동차등록증"을 요구·OCR. 굴삭기여도 자동차등록증.

**사실관계**:
- `EquipmentCategory` 9종(EXCAVATOR·WHEEL_LOADER·CRANE·FORKLIFT·DOZER·GRADER·AERIAL_LIFT·PUMP_TRUCK·ATTACHMENT)은 **전부 건설기계** → 건설기계등록증(id32)이 맞는 등록증.
- 자동차등록증(id7)은 트레일러/화물 등 자동차관리법 대상 **차량**용인데, 그 종류가 enum에 없음.
- 필수서류는 수퍼어드민 `DocumentTypeAdminPage`에서 서류별 `required`+`applies_to_categories`(장비종류 CSV)로 지정 가능(BE `DocumentTypeAdminController`). **폼이 이 설정을 안 따르고 하드코딩**했던 게 문제.

**수정 — 설정 기반화(하드코딩 제거)**:
- `EquipmentCreateForm`:
  - `pickRegistrationType(types, category)`: `active && ocr_region_template 보유 && ownerMatches(카테고리)` 후보 중 `required` 우선 → 해당 종류의 등록증 1개 선택. (자동차·건설기계 템플릿 모두 `vehicle_no·model·year` 키라 폼 프리필 로직 호환.)
  - 장비 종류 선택을 **맨 위로** 이동, `EquipmentFields`에 `hideCategory` prop 추가(중복 방지).
  - OCR "시작" 블록 제목·파일선택·첨부·정렬대상 전부 `regDocType` 기준으로 동적화. 템플릿 없는 종류는 "직접 입력" 폴백.
- **V86**(`V86__equipment_registration_required.sql`): 건설기계등록증 `required=TRUE` + `applies_to_categories`=9종 스코프; 자동차등록증 `required=FALSE`(전체 필수면 건설기계에도 요구되는 오류 방지). *로컬은 동일 SQL 즉시반영 + V86은 재기동/재시드 영속용.*

**검증**: `pickRegistrationType(EXCAVATOR)` → 건설기계등록증. 건설기계 샘플 E2E: 연식=2015·차량번호=004라4248·차명=D30S-7·차대번호=FDA0R-1000-01154 **전 필드 추출** + warp 2.4MB.

## 3. 자동차등록증 템플릿 재보정 (V87)
**증상**: `등록증여백.png`에서 자동차등록증(id7) 추출이 전부 빈 값.
**원인(둘 다)**: ① detect-corners 오검출(오른쪽아래를 425로, 실제 종이는 ~583) → warp 왜곡. ② 템플릿 박스가 이 발급본 레이아웃과 어긋남(필드가 다른 행에 떨어짐).
**조치**: 문서 4모서리로 canonical warp 후 전체 OCR 로 필드 좌표 재측정 → 박스 교체(V87, `V87__vehicle_registration_template_recalibrate.sql`). corners를 문서 4모서리에 맞추면:
- vehicle_no=83사1725·model=수림22.6톤저상트레일러·year=2016·vin=KN9ENEZTZGNBWJ006 **추출 성공**(인증API 실측, warp 4.2MB).
- expiry_date = 최신 검사유효기간이 **수기 기재**라 OCR 불안정 → 수동 보정.

## 4. 밴드+패턴 추출로 전환 (V88) — 고정영역 fragility 해결
**문제**: 고정영역 크롭 템플릿(V87)은 **손정렬 4모서리 코너 편차**에 박스가 통째로 밀려, 값이 다른 칸으로 오추출(브라우저 실사용에서 확인 — 차량번호가 모델명 칸으로). API를 내가 고른 정확한 코너로만 검증한 게 오류였음.
**해결(paddle `document_align.py`)**:
- **밴드 모드(`kind`)**: 넉넉한 밴드 안에서 **값의 패턴**으로 추출(`_pick_value`) — plate(`\d{2,3}[가-힣]\d{4}`)·hangul(라벨어 제외 최장 한글)·year(`(19|20)\d{2}`)·vin·date. 라벨 위치·코너 편차에 무관.
- **스캔보정(`_enhance_for_ocr`)**: warp 후 그레이스케일·CLAHE·언샵 → OCR 정확도↑ + 미리보기 '선명'(사용자 요청). `return_warped`도 보정본 반환.
- 템플릿 `fields[].kind` 있으면 밴드 모드, 없으면 기존 box+parser(하위호환).
**자동차등록증 템플릿(V88)**: 3필드만 — vehicle_no(plate)·model(hangul)·year(최초등록일). 밴드 box + kind.
**검증**: 코너 A·C(서로 다르게) 백엔드 API 결과 **동일**(vno=83사1725·year=2015·model 일치). ~4-5초. **단 OCR 글자 미세오독은 잔존(수동보정)**. FE 자동입력 3필드 재활성(오탈자 안내) + 확대(줌) 모달.
**미확인**: 실제 브라우저 E2E는 **사용자 확인 대기**. 건설기계등록증도 동일 밴드 전환 예정(자동차 확인 후).

## 미결 (사용자 확인 대기)
- **차량 종류 추가**: 자동차등록증을 **필수**로 걸려면 트레일러/화물/카고 등 차량 종류를 `EquipmentCategory`(백엔드)+`types/equipment`(FE)에 추가하고, 자동차등록증을 그 종류에 `required`+스코프. 지금은 차량 종류가 없어 자동차등록증을 비필수로 둠.
- **detect-corners 오검출(자동차 샘플)**: 어두운 배경+저대비 하단에서 오른쪽아래 코너를 놓침 → 사용자가 4모서리 UI에서 보정해야 정확 추출(건설기계 샘플은 자동검출 양호). 자동검출 개선은 별도 후속.
- **앵커(라벨) 기반 추출**: 발급본 편차에 근본적으로 강하지만 이전 프로토타입 부분성공 — 보류(현재는 종류별 고정영역 템플릿 + 수동보정 방침).

## 헬스 참고
`/actuator/health` DOWN = dev에 SMTP 없어 mail 인디케이터 실패 추정(무해·기존). 로그인·쿼리·OCR·문서API 전부 정상.
