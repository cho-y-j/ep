// 브라우저에서 동작하는 작업계획서 DOCX 생성 엔진
// - fetch로 템플릿/이미지 로드
// - docxtemplater + pizzip로 placeholder 치환
// - PizZip low-level 편집으로 뒤쪽 페이지에 첨부 이미지 삽입
import PizZip from "pizzip";
import Docxtemplater from "docxtemplater";
// @ts-ignore — no types ship with the module
import ImageModule from "docxtemplater-image-module-free";
import { normalizeForRender } from "./schema";
import { tokenStorage } from "../tokenStorage";

const TEMPLATE_URL = "/worksheet/template.docx";
// 이미지 로드: skep document-service의 /api/documents/{id}/file (인증 필요)
const DOC_FILE_URL = (id: string) => `/api/documents/${id}/file`;

export interface Attachment {
  storageKey: string; // "<uuid>-<filename>.jpg" — IMAGE_BASE 에 상대
  category: string;
  originalName?: string;
  /** image/* 가 아니면 (예: application/pdf) DOCX 내부에 그림으로 못 넣음 — placeholder 페이지로 표시. */
  mimeType?: string;
}

// 템플릿 버퍼 캐싱 (한번만 받음)
let _templateBuf: ArrayBuffer | null = null;
async function loadTemplate(): Promise<ArrayBuffer> {
  if (_templateBuf) return _templateBuf;
  const res = await fetch(TEMPLATE_URL);
  if (!res.ok) throw new Error(`템플릿 로드 실패: ${res.status}`);
  _templateBuf = await res.arrayBuffer();
  return _templateBuf;
}

// 이미지 버퍼 캐싱 (LRU-ish). SPA 장시간 세션에서 수십 MB 누적 방지용 용량 제한.
const IMAGE_CACHE_MAX = 40;
const _imageCache = new Map<string, ArrayBuffer>();
async function loadImage(storageKey: string): Promise<ArrayBuffer | null> {
  const cached = _imageCache.get(storageKey);
  if (cached) {
    // 재접근 시 Map 재삽입 → 최근성 갱신
    _imageCache.delete(storageKey);
    _imageCache.set(storageKey, cached);
    return cached;
  }
  try {
    const token = typeof localStorage !== "undefined" ? tokenStorage.access : null;
    const res = await fetch(DOC_FILE_URL(storageKey), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) return null;
    const buf = await res.arrayBuffer();
    if (_imageCache.size >= IMAGE_CACHE_MAX) {
      const oldest = _imageCache.keys().next().value;
      if (oldest) _imageCache.delete(oldest);
    }
    _imageCache.set(storageKey, buf);
    return buf;
  } catch {
    return null;
  }
}

// (skep 원본 보존 — compressImage 가 createImageBitmap 으로 대체해 미사용)
// function getImageDimensions(blob: Blob): Promise<{ width: number; height: number }> { ... }

// 긴 변 1600px 이하 + JPEG 품질 0.82로 리샘플링 → DOCX/PDF 용량 대폭 축소
// 반환에 width/height 포함 → 호출자가 getImageDimensions 재실행 불필요
async function compressImage(buf: ArrayBuffer, mime: string): Promise<{ buf: ArrayBuffer; ext: string; width: number; height: number }> {
  const MAX_DIM = 1600;
  // Early return: 이미 충분히 작은 JPEG은 bitmap 디코딩 자체를 건너뛴다
  const fallbackExt = mime === "image/jpeg" ? "jpg" : "png";
  try {
    const blob = new Blob([buf], { type: mime || "image/png" });
    const bitmap = await createImageBitmap(blob);
    let w = bitmap.width, h = bitmap.height;
    if (w <= MAX_DIM && h <= MAX_DIM && mime === "image/jpeg" && buf.byteLength < 400_000) {
      bitmap.close?.();
      return { buf, ext: "jpg", width: w, height: h };
    }
    if (w > MAX_DIM || h > MAX_DIM) {
      const r = Math.min(MAX_DIM / w, MAX_DIM / h);
      w = Math.round(w * r); h = Math.round(h * r);
    }
    const canvas = document.createElement("canvas");
    canvas.width = w; canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (!ctx) { bitmap.close?.(); return { buf, ext: fallbackExt, width: bitmap.width, height: bitmap.height }; }
    ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, w, h);
    ctx.drawImage(bitmap, 0, 0, w, h);
    bitmap.close?.();
    const outBlob: Blob | null = await new Promise((res) => canvas.toBlob(res, "image/jpeg", 0.82));
    if (!outBlob) return { buf, ext: fallbackExt, width: w, height: h };
    const newBuf = await outBlob.arrayBuffer();
    return { buf: newBuf, ext: "jpg", width: w, height: h };
  } catch {
    return { buf, ext: fallbackExt, width: 1600, height: 1200 };
  }
}

const escapeXml = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");

async function appendAttachments(zip: PizZip, attachments: Attachment[]): Promise<void> {
  if (!attachments.length) return;

  const docXmlPath = "word/document.xml";
  const relsPath = "word/_rels/document.xml.rels";
  const ctPath = "[Content_Types].xml";

  let docXml = zip.file(docXmlPath)!.asText();
  let relsXml = zip.file(relsPath)!.asText();
  let ctXml = zip.file(ctPath)!.asText();

  const existingRids = [...relsXml.matchAll(/Id="rId(\d+)"/g)].map((m) => parseInt(m[1]));
  let nextRid = (existingRids.length ? Math.max(...existingRids) : 0) + 1;

  const mediaExisting = [...Object.keys(zip.files).join("\n").matchAll(/word\/media\/image(\d+)\./g)].map((m) =>
    parseInt(m[1])
  );
  let nextImgIdx = (mediaExisting.length ? Math.max(...mediaExisting) : 0) + 1;

  if (!/Extension="jpg"|Extension="jpeg"/i.test(ctXml))
    ctXml = ctXml.replace("</Types>", '<Default Extension="jpg" ContentType="image/jpeg"/></Types>');
  if (!/Extension="png"/i.test(ctXml))
    ctXml = ctXml.replace("</Types>", '<Default Extension="png" ContentType="image/png"/></Types>');
  if (!/Extension="jpeg"/i.test(ctXml))
    ctXml = ctXml.replace("</Types>", '<Default Extension="jpeg" ContentType="image/jpeg"/></Types>');

  const paragraphs: string[] = [
    '<w:p><w:r><w:br w:type="page"/></w:r></w:p>',
    '<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="400" w:after="200"/></w:pPr>' +
      '<w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t>첨부 서류</w:t></w:r></w:p>',
  ];

  // 이미지 로드 + 압축을 모든 첨부에 대해 병렬로.
  // mimeType 이 image/* 가 아니면 (PDF 등) DOCX 그림으로 못 넣음 → null 반환, 아래에서 placeholder paragraph 만 추가.
  const processed = await Promise.all(attachments.map(async (att) => {
    const mime0 = (att.mimeType ?? "").toLowerCase();
    const nameExt = (att.originalName || att.storageKey).match(/\.([a-zA-Z0-9]+)$/)?.[1]?.toLowerCase();
    const isImage = mime0.startsWith("image/")
        || (mime0 === "" && (nameExt === "jpg" || nameExt === "jpeg" || nameExt === "png" || nameExt === "gif" || nameExt === "webp"));
    if (!isImage) {
      // PDF/기타 — 이미지로 못 넣음. placeholder 만 표시.
      return { att, skip: true } as const;
    }
    const rawBuf = await loadImage(att.storageKey);
    if (!rawBuf) return null;
    const mime = (mime0 === "image/png" || nameExt === "png") ? "image/png" : "image/jpeg";
    const compressed = await compressImage(rawBuf, mime);
    return { att, skip: false as const, ...compressed };
  }));

  for (let i = 0; i < processed.length; i++) {
    const item = processed[i];
    if (!item) continue;
    // 이미지 아닌 첨부 (PDF 등) → 페이지 break + 카테고리/파일명/안내만 표시. DOCX 손상 방지.
    if (item.skip) {
      const att = item.att;
      const label = escapeXml(`[${i + 1}/${attachments.length}] ${att.category}${att.originalName ? " · " + att.originalName : ""}`);
      paragraphs.push(
        '<w:p><w:r><w:br w:type="page"/></w:r></w:p>',
        '<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="120"/></w:pPr>' +
          `<w:r><w:rPr><w:b/><w:sz w:val="24"/></w:rPr><w:t>${label}</w:t></w:r></w:p>`,
        '<w:p><w:pPr><w:jc w:val="center"/></w:pPr>' +
          '<w:r><w:rPr><w:color w:val="888888"/><w:sz w:val="20"/></w:rPr>' +
          '<w:t>(이미지가 아닌 첨부 — 별도 다운로드해주세요)</w:t></w:r></w:p>'
      );
      continue;
    }
    const { att, buf, ext, width, height } = item;

    const mediaName = `image${nextImgIdx}.${ext}`;
    zip.file(`word/media/${mediaName}`, buf);

    const rid = `rId${nextRid}`;
    relsXml = relsXml.replace(
      "</Relationships>",
      `<Relationship Id="${rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${mediaName}"/></Relationships>`
    );

    let cx = 5760000;
    let cy = 4320000;
    if (width > 0 && height > 0) {
      const aspect = height / width;
      cy = Math.round(cx * aspect);
      const maxCy = 7200000;
      if (cy > maxCy) {
        cy = maxCy;
        cx = Math.round(cy / aspect);
      }
    }

    const docPrId = nextImgIdx;
    const label = escapeXml(
      `[${i + 1}/${attachments.length}] ${att.category}${att.originalName ? " · " + att.originalName : ""}`
    );

    paragraphs.push(
      '<w:p><w:r><w:br w:type="page"/></w:r></w:p>',
      '<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="120"/></w:pPr>' +
        `<w:r><w:rPr><w:b/><w:sz w:val="24"/></w:rPr><w:t>${label}</w:t></w:r></w:p>`,
      '<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:noProof/></w:rPr>' +
        '<w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">' +
        `<wp:extent cx="${cx}" cy="${cy}"/><wp:effectExtent l="0" t="0" r="0" b="0"/>` +
        `<wp:docPr id="${docPrId}" name="Attachment ${docPrId}"/>` +
        '<wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/></wp:cNvGraphicFramePr>' +
        '<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">' +
        '<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">' +
        '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">' +
        `<pic:nvPicPr><pic:cNvPr id="${docPrId}" name="${escapeXml(mediaName)}"/><pic:cNvPicPr/></pic:nvPicPr>` +
        `<pic:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="${rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>` +
        `<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>` +
        "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    );

    nextRid++;
    nextImgIdx++;
  }

  const sectPrMatch = docXml.match(/<w:sectPr[\s\S]*?<\/w:sectPr>/);
  const insertion = paragraphs.join("");
  if (sectPrMatch) docXml = docXml.replace(sectPrMatch[0], insertion + sectPrMatch[0]);
  else docXml = docXml.replace("</w:body>", insertion + "</w:body>");

  zip.file(docXmlPath, docXml);
  zip.file(relsPath, relsXml);
  zip.file(ctPath, ctXml);
}

// 같은 서식(rPr)을 가진 인접 <w:r> run을 하나로 병합.
// Word가 편집 이력으로 쪼개놓은 run을 통합해 OnlyOffice에서 하나의 블록으로 선택·편집 가능하게 함.
function mergeAdjacentRuns(xml: string): string {
  const runPattern = /<w:r(?:\s+[^>]*)?>(<w:rPr>[\s\S]*?<\/w:rPr>)?<w:t(\s+[^>]*)?>([\s\S]*?)<\/w:t><\/w:r>/g;
  type Item = { rPr: string; text: string; hasSpace: boolean };
  const result: string[] = [];
  let lastIdx = 0;
  let buffer: Item[] = [];
  const flush = () => {
    if (buffer.length === 0) return;
    if (buffer.length === 1) {
      const it = buffer[0];
      const spaceAttr = it.hasSpace ? ' xml:space="preserve"' : "";
      result.push(`<w:r>${it.rPr}<w:t${spaceAttr}>${it.text}</w:t></w:r>`);
    } else {
      const merged = buffer.map((x) => x.text).join("");
      result.push(`<w:r>${buffer[0].rPr}<w:t xml:space="preserve">${merged}</w:t></w:r>`);
    }
    buffer = [];
  };

  let m: RegExpExecArray | null;
  while ((m = runPattern.exec(xml)) !== null) {
    const between = xml.slice(lastIdx, m.index);
    lastIdx = m.index + m[0].length;
    const rPr = m[1] || "";
    const hasSpace = /xml:space\s*=\s*"preserve"/.test(m[2] || "");
    const text = m[3];
    if (between.trim().length > 0) {
      flush();
      result.push(between);
    } else if (buffer.length > 0 && buffer[buffer.length - 1].rPr !== rPr) {
      flush();
    }
    buffer.push({ rPr, text, hasSpace });
  }
  flush();
  result.push(xml.slice(lastIdx));
  return result.join("");
}

/** 5개 사인란 placeholder 키 (DOCX 템플릿에서 {%authorSign} 등으로 참조). */
export interface SignatureImages {
  authorSign?: string | null;     // base64 PNG (no data: prefix) — 작성자
  supervisorSign?: string | null; // 담당자
  confirmerSign?: string | null;  // 확인자
  reviewerSign?: string | null;   // 검토자
  approverSign?: string | null;   // 승인자
}

/** base64 → Uint8Array (image module 입력) */
function b64ToBytes(b64: string): Uint8Array {
  const clean = b64.includes(",") ? b64.substring(b64.indexOf(",") + 1) : b64;
  const bin = atob(clean);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export async function renderWorksheet(
  values: Record<string, unknown>,
  workSiteDiagramKey: string = "",
  attachments: Attachment[] = [],
  signatures: SignatureImages = {}
): Promise<Blob> {
  const templateBuf = await loadTemplate();
  const zip = new PizZip(templateBuf);

  // 사인 이미지 모듈: placeholder 가 base64 string 일 때만 PNG 로 치환.
  // 빈 문자열/undefined 이면 텍스트 ""로 fallback (image-module-free 가 throw 하지 않게 빈 1x1 처리).
  const imageModule = new ImageModule({
    centered: true,
    fileType: "docx",
    getImage: (tagValue: string) => {
      if (!tagValue) {
        // 1x1 투명 PNG (사인 없을 때 자리 비움)
        return new Uint8Array([
          0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
          0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4,
          0x89, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9c, 0x63, 0xfc, 0xff, 0xff, 0x3f,
          0x00, 0x05, 0xfe, 0x02, 0xfe, 0xa3, 0x35, 0xc7, 0xb1, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e,
          0x44, 0xae, 0x42, 0x60, 0x82,
        ]);
      }
      return b64ToBytes(tagValue);
    },
    getSize: () => [180, 60], // 사인란 기본 크기 (px) — 템플릿에 따라 조정
  });

  // 실제 사인/도면 데이터가 한 개라도 있을 때만 ImageModule 활성화.
  // 빈 데이터에서 ImageModule 가 1x1 PNG 6개를 docx 에 박으면 일부 OnlyOffice 환경에서
  // docx 검증 실패 (errorCode -85) 가 발생.
  const hasAnyImage = !!workSiteDiagramKey
      || !!signatures.authorSign || !!signatures.supervisorSign
      || !!signatures.confirmerSign || !!signatures.reviewerSign
      || !!signatures.approverSign;
  const doc = new Docxtemplater(zip, {
    paragraphLoop: true,
    linebreaks: true,
    nullGetter: () => "",
    modules: hasAnyImage ? [imageModule] : [],
  });

  const data: Record<string, unknown> = {
    ...normalizeForRender(values as Record<string, unknown>),
    workSiteDiagram: workSiteDiagramKey,
    // 5개 사인 (image-module-free 가 {%authorSign} 등 placeholder 발견 시 PNG 삽입)
    authorSign:     signatures.authorSign     ?? "",
    supervisorSign: signatures.supervisorSign ?? "",
    confirmerSign:  signatures.confirmerSign  ?? "",
    reviewerSign:   signatures.reviewerSign   ?? "",
    approverSign:   signatures.approverSign   ?? "",
  };

  doc.render(data);

  await appendAttachments(zip, attachments);

  // 템플릿 유래 또는 docxtemplater 치환 결과로 같은 서식의 run들이 쪼개져있으면
  // OnlyOffice가 블록을 분리해서 편집을 방해한다. 병합 후처리로 하나의 run으로 통합.
  try {
    const docXml = zip.file("word/document.xml")?.asText();
    if (docXml) {
      const merged = mergeAdjacentRuns(docXml);
      zip.file("word/document.xml", merged);
    }
  } catch (e) {
    console.warn("run merge skipped:", e);
  }

  // PizZip 기본은 STORED — 일부 docx 처리기(특히 OnlyOffice docservice)가 STORED docx 에서
  // "파일 내용이 파일 확장명과 일치하지 않음" 형태로 거부하는 경우가 있어 DEFLATE 강제.
  const out = doc.getZip().generate({
    type: "uint8array",
    compression: "DEFLATE",
    compressionOptions: { level: 6 },
  }) as Uint8Array;
  return new Blob([out as BlobPart], {
    type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
}

// ─── 업로드 템플릿(사용자 워드 파일) 처리 ────────────────────────────────
// 사용자가 업로드한 워드 파일에서 {{name}} 형식 플레이스홀더 스캔 + 치환

export interface ScannedTemplate {
  zip: PizZip;
  xml: string;           // merge 적용된 document.xml
  placeholders: string[]; // unique, 순서대로
}

// 파일을 받아 run merge 후 {{placeholder}} 추출
export async function scanUploadedTemplate(file: File): Promise<ScannedTemplate> {
  const buf = await file.arrayBuffer();
  const zip = new PizZip(buf);
  const docFile = zip.file("word/document.xml");
  if (!docFile) throw new Error("올바른 워드 파일이 아닙니다 (word/document.xml 없음)");
  // Word가 {{...}} 를 여러 run으로 쪼개는 경우가 있어, 같은 서식의 인접 run 병합으로 우선 정리
  const merged = mergeAdjacentRuns(docFile.asText());
  // 모든 <w:t> 내부에서 {{...}} 뽑아냄
  const placeholders = new Set<string>();
  const tPattern = /<w:t[^>]*>([\s\S]*?)<\/w:t>/g;
  let m: RegExpExecArray | null;
  while ((m = tPattern.exec(merged)) !== null) {
    const text = m[1];
    const phPattern = /\{\{\s*([^}\s][^}]*?)\s*\}\}/g;
    let p: RegExpExecArray | null;
    while ((p = phPattern.exec(text)) !== null) {
      placeholders.add(p[1]);
    }
  }
  return { zip, xml: merged, placeholders: [...placeholders] };
}

// 줄바꿈은 단일 공백으로 치환 후 XML 이스케이프.
// WHY: `{{name}}` 치환 값이 w:t 내부 텍스트로 들어가는데, w:t 는 줄바꿈을 <w:br/>로
//       풀어줘야 표시되고 이는 런 분할이 필요해 복잡함. 자동 채움 값은 대부분 한 줄이라 공백 치환.
function xmlEscapeForRun(s: string): string {
  return escapeXml(s.replace(/\n+/g, " "));
}

// 치환: {{name}} 을 values[name] 으로 대체. 모르면 빈 문자열.
export function fillScannedTemplate(tmpl: ScannedTemplate, values: Record<string, string>): Blob {
  const replaced = tmpl.xml.replace(/\{\{\s*([^}\s][^}]*?)\s*\}\}/g, (_, name: string) => {
    const key = name.trim();
    const v = values[key];
    return v !== undefined && v !== null ? xmlEscapeForRun(String(v)) : "";
  });
  tmpl.zip.file("word/document.xml", replaced);
  const out = tmpl.zip.generate({
    type: "uint8array",
    compression: "DEFLATE",
    compressionOptions: { level: 6 },
  }) as Uint8Array;
  return new Blob([out as BlobPart], {
    type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
}

// 자동 매핑 컨텍스트 — 장비·인원·회사·현장 전체 필드 지원
export interface PersonCtx {
  name?: string;
  birth?: string;         // 생년월일
  phone?: string;
  address?: string;
  licenseNo?: string;     // 면허번호 / 자격증번호
  licenseType?: string;   // 면허종류
  company?: string;
}

export interface EquipmentCtx {
  equipmentType?: string;   // 고소작업차 / 타워크레인 ...
  vehicleNo?: string;
  name?: string;            // 장비명 (모델명)
  model?: string;
  manufacturer?: string;
  year?: string;            // 제조년도
  upperPartYear?: string;   // 상부년식
  serialNo?: string;        // 차대번호
  capacity?: string;        // 정격하중
  insuranceExpiry?: string;
  inspectionExpiry?: string;
  ndtExpiry?: string;
}

export interface CompanyCtx {
  name?: string;
  businessNumber?: string;
  representative?: string;
  address?: string;
  phone?: string;
  email?: string;
}

export interface PlaceholderCtx {
  equipment?: EquipmentCtx;
  supplier?: CompanyCtx;
  bp?: CompanyCtx;
  operators?: PersonCtx[];
  supervisors?: PersonCtx[];
  signalmen?: PersonCtx[];
  firewatchers?: PersonCtx[];
  signalers?: PersonCtx[];
  siteName?: string;
  siteAddress?: string;
  startDate?: string;
  endDate?: string;
  writer?: string;
}

function joinNames(arr?: PersonCtx[]): string {
  return (arr && arr.length) ? arr.map(p => p.name || "").filter(Boolean).join(", ") : "";
}

function personFields(prefix: string, arr: PersonCtx[] | undefined, dict: Record<string, string>) {
  if (!arr || arr.length === 0) return;
  const first = arr[0];
  // 역할_이름, 역할_생년월일 같은 프리픽스 + 첫 번째 인원 정보
  dict[`${prefix}_이름`] = first.name || "";
  dict[`${prefix}_성명`] = first.name || "";
  dict[`${prefix}_생년월일`] = first.birth || "";
  dict[`${prefix}_면허번호`] = first.licenseNo || "";
  dict[`${prefix}_면허종류`] = first.licenseType || "";
  dict[`${prefix}_자격증번호`] = first.licenseNo || "";
  dict[`${prefix}_연락처`] = first.phone || "";
  dict[`${prefix}_주소`] = first.address || "";
  dict[`${prefix}_소속`] = first.company || "";
  // 역할_1, 역할_2 … 이름 인덱스 접근
  arr.forEach((p, i) => {
    dict[`${prefix}_${i + 1}`] = p.name || "";
  });
  // 역할 단독 = 쉼표 조인
  dict[prefix] = joinNames(arr);
  // 역할_전원 = 줄바꿈 조인
  dict[`${prefix}_전원`] = (arr.map(p => p.name || "").filter(Boolean).join("\n"));
}

// 시스템이 아는 표준 키 → 현재 세션의 실제 값으로 자동 매핑
export function autoFillKnownPlaceholders(
  placeholders: string[],
  context: PlaceholderCtx
): Record<string, string> {
  const today = new Date().toISOString().slice(0, 10);
  const eq = context.equipment || {};
  const sup = context.supplier || {};
  const bp = context.bp || {};
  // 작업기간 계산
  let duration = "";
  if (context.startDate && context.endDate) {
    try {
      const s = new Date(context.startDate);
      const e = new Date(context.endDate);
      const days = Math.max(0, Math.round((e.getTime() - s.getTime()) / 86400000) + 1);
      duration = `${days}일`;
    } catch { /* noop */ }
  }

  const dict: Record<string, string> = {
    // ─── 장비 ────────────────────────────
    "차량번호": eq.vehicleNo || "",
    "장비종류": eq.equipmentType || "",
    "장비명": eq.name || eq.model || "",
    "장비모델": eq.model || "",
    "모델명": eq.model || "",
    "제조사": eq.manufacturer || "",
    "제조년도": eq.year || "",
    "제조연도": eq.year || "",
    "상부년식": eq.upperPartYear || eq.year || "",
    "차대번호": eq.serialNo || "",
    "정격하중": eq.capacity || "",
    "보험만료일": eq.insuranceExpiry || "",
    "검사만료일": eq.inspectionExpiry || "",
    "비파괴검사만료일": eq.ndtExpiry || "",

    // ─── 현장 ────────────────────────────
    "현장명": context.siteName || "",
    "현장주소": context.siteAddress || "",
    "작성일": today,
    "작성자": context.writer || "",
    "시작일": context.startDate || "",
    "종료일": context.endDate || "",
    "작업기간": duration,

    // ─── 공급사 ──────────────────────────
    "공급사": sup.name || "",
    "공급사명": sup.name || "",
    "공급사_사업자번호": sup.businessNumber || "",
    "공급사_대표": sup.representative || "",
    "공급사대표": sup.representative || "",
    "공급사_주소": sup.address || "",
    "공급사_전화": sup.phone || "",
    "공급사_이메일": sup.email || "",

    // ─── BP (발주/원청) ──────────────────
    "BP": bp.name || "",
    "BP명": bp.name || "",
    "발주사": bp.name || "",
    "원청사": bp.name || "",
    "BP_사업자번호": bp.businessNumber || "",
    "BP_대표": bp.representative || "",
    "BP_주소": bp.address || "",
    "BP_전화": bp.phone || "",

    // ─── 영문 alias (기존 호환) ─────────
    "vehicleNo": eq.vehicleNo || "",
    "siteName": context.siteName || "",
    "today": today,
  };

  // 역할별 세부 필드 확장
  personFields("조종원", context.operators, dict);
  personFields("작업지휘자", context.supervisors, dict);
  personFields("유도원", context.signalmen, dict);
  personFields("화기감시자", context.firewatchers, dict);
  personFields("신호수", context.signalers, dict);

  const out: Record<string, string> = {};
  for (const p of placeholders) {
    if (dict[p] !== undefined) out[p] = dict[p];
  }
  return out;
}

// 사용자가 참조할 수 있는 표준 플레이스홀더 카탈로그 (UI에 표시)
export const PLACEHOLDER_CATALOG: { section: string; items: { key: string; desc: string }[] }[] = [
  {
    section: "장비",
    items: [
      { key: "차량번호", desc: "선택한 장비의 차량번호" },
      { key: "장비종류", desc: "예: 고소작업차, 타워크레인" },
      { key: "장비모델", desc: "모델명" },
      { key: "제조사", desc: "" },
      { key: "제조년도", desc: "" },
      { key: "상부년식", desc: "" },
      { key: "차대번호", desc: "시리얼 번호" },
      { key: "정격하중", desc: "" },
      { key: "보험만료일", desc: "" },
      { key: "검사만료일", desc: "" },
    ],
  },
  {
    section: "현장 / 일정",
    items: [
      { key: "현장명", desc: "" },
      { key: "현장주소", desc: "" },
      { key: "시작일", desc: "" },
      { key: "종료일", desc: "" },
      { key: "작업기간", desc: "자동 계산 (예: 7일)" },
      { key: "작성일", desc: "오늘 날짜" },
      { key: "작성자", desc: "" },
    ],
  },
  {
    section: "공급사 / BP",
    items: [
      { key: "공급사명", desc: "" },
      { key: "공급사_사업자번호", desc: "" },
      { key: "공급사_대표", desc: "" },
      { key: "공급사_주소", desc: "" },
      { key: "공급사_전화", desc: "" },
      { key: "BP명", desc: "발주/원청사" },
      { key: "BP_사업자번호", desc: "" },
      { key: "BP_대표", desc: "" },
    ],
  },
  {
    section: "인원 (역할별)",
    items: [
      { key: "조종원", desc: "전원 이름 (쉼표 조인)" },
      { key: "조종원_이름", desc: "첫 번째 조종원 이름" },
      { key: "조종원_면허번호", desc: "첫 번째 조종원" },
      { key: "조종원_생년월일", desc: "" },
      { key: "조종원_연락처", desc: "" },
      { key: "조종원_1", desc: "첫 번째 조종원 (인덱스 접근)" },
      { key: "조종원_2", desc: "두 번째 조종원" },
      { key: "작업지휘자_이름", desc: "역할 프리픽스 + 필드 조합 가능" },
      { key: "유도원_전원", desc: "줄바꿈 조인" },
    ],
  },
];

// 템플릿의 라벨 텍스트 → placeholder 매핑 추출 (매칭 맵에 사용)
let _labelAliases: Record<string, string> | null = null;
export async function extractLabelAliases(): Promise<Record<string, string>> {
  if (_labelAliases) return _labelAliases;
  const buf = await loadTemplate();
  const zip = new PizZip(buf);
  const doc = zip.file("word/document.xml")!.asText();
  const texts = [...doc.matchAll(/<w:t[^>]*>([^<]+)<\/w:t>/g)].map((m) => m[1]);
  const map: Record<string, string> = {};
  for (let i = 1; i < texts.length; i++) {
    const t = texts[i];
    const phMatch = t.match(/\{([a-zA-Z_][a-zA-Z0-9_]*)\}/);
    if (!phMatch) continue;
    const key = phMatch[1];
    const prev = texts[i - 1].trim();
    if (prev.length >= 2 && !prev.includes("{") && !/^[☑☐()·]+$/.test(prev) && !map[prev]) {
      map[prev] = key;
    }
  }
  _labelAliases = map;
  return map;
}
