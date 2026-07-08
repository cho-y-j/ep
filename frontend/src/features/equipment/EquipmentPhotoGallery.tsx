import { useEffect, useRef, useState } from 'react';
import { api } from '../../lib/api';
import type { DocumentResponse } from '../../types/document';

type Props = {
  equipmentId: number;
  hasPhoto: boolean;
  category: string;
};

type Photo = { id: string; label: string; url: string };

// 갤러리에 표시할 사진 종류만. 서류(자동차등록증/보험증권/안전인증서/검사증/점검표)는
// 별도 서류 섹션에서 노출되므로 갤러리에서 제외.
const PHOTO_DOCUMENT_TYPES = new Set(['장비 전면사진', '장비 측면사진']);

export default function EquipmentPhotoGallery({ equipmentId, hasPhoto }: Props) {
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [selected, setSelected] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const [lightbox, setLightbox] = useState(false);
  const urlsRef = useRef<string[]>([]);

  useEffect(() => {
    let cancelled = false;
    const collected: Photo[] = [];
    const newUrls: string[] = [];

    async function load() {
      // 메인 photo_key (전면 thumbnail)
      if (hasPhoto) {
        try {
          const res = await api.get(`/api/equipment/${equipmentId}/photo`, { responseType: 'blob' });
          const url = URL.createObjectURL(res.data);
          newUrls.push(url);
          collected.push({ id: 'main', label: '대표 사진', url });
        } catch { /* ignore */ }
      }
      // documents 중 image/* 만
      try {
        const res = await api.get<DocumentResponse[]>('/api/documents', {
          params: { ownerType: 'EQUIPMENT', ownerId: equipmentId },
        });
        const images = res.data.filter(
          (d) => d.content_type?.startsWith('image/') && PHOTO_DOCUMENT_TYPES.has(d.document_type_name),
        );
        const blobs = await Promise.all(
          images.map((d) =>
            api.get(`/api/documents/${d.id}/file`, { responseType: 'blob' })
              .then((r) => ({ doc: d, url: URL.createObjectURL(r.data) }))
              .catch(() => null)
          )
        );
        for (const item of blobs) {
          if (!item) continue;
          newUrls.push(item.url);
          collected.push({
            id: `doc-${item.doc.id}`,
            label: item.doc.document_type_name,
            url: item.url,
          });
        }
      } catch { /* ignore */ }

      if (!cancelled) {
        urlsRef.current.forEach((u) => URL.revokeObjectURL(u));
        urlsRef.current = newUrls;
        setPhotos(collected);
        setSelected(0);
      } else {
        newUrls.forEach((u) => URL.revokeObjectURL(u));
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [equipmentId, hasPhoto]);

  // unmount 시 잔여 URL 해제
  useEffect(() => () => urlsRef.current.forEach((u) => URL.revokeObjectURL(u)), []);

  const main = photos[selected];
  const total = photos.length;
  const visibleCount = expanded ? total : Math.min(4, total);
  const extra = total - 4; // +N 오버레이용
  const showOverlay = !expanded && extra > 0;

  return (
    <div className="space-y-3">
      <button
        type="button"
        onClick={() => main && setLightbox(true)}
        className="block aspect-[4/3] w-full overflow-hidden rounded-xl bg-slate-100 group"
        disabled={!main}
      >
        {main ? (
          <img src={main.url} alt={main.label} className="w-full h-full object-cover group-hover:scale-[1.01] transition-transform" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-slate-300">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
              <rect x="2" y="10" width="20" height="8" rx="1.5" />
              <circle cx="7" cy="19" r="2" />
              <circle cx="17" cy="19" r="2" />
              <path d="M5 10V6h6l3 4" />
            </svg>
          </div>
        )}
      </button>

      {total > 0 && (
        <>
          <div className={`grid gap-2 ${expanded ? 'grid-cols-4 sm:grid-cols-5' : 'grid-cols-4'}`}>
            {photos.slice(0, visibleCount).map((p, i) => {
              const isLast = !expanded && i === 3 && showOverlay;
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => (isLast ? setExpanded(true) : setSelected(i))}
                  className={`aspect-square overflow-hidden rounded-lg bg-slate-100 relative border-2 transition ${
                    selected === i && !isLast ? 'border-brand-500' : 'border-transparent hover:border-slate-300'
                  }`}
                  title={p.label}
                >
                  <img src={p.url} alt={p.label} className="w-full h-full object-cover" />
                  {isLast && (
                    <div className="absolute inset-0 bg-slate-900/60 flex items-center justify-center text-white font-semibold text-sm">
                      +{extra}
                    </div>
                  )}
                </button>
              );
            })}
          </div>
          {expanded && total > 4 && (
            <button
              type="button"
              onClick={() => setExpanded(false)}
              className="text-xs text-slate-500 hover:text-slate-900"
            >
              접기 ▴
            </button>
          )}
          <p className="text-xs text-slate-400">{main?.label} · {selected + 1} / {total}</p>
        </>
      )}

      {lightbox && main && (
        <div
          className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4"
          onClick={() => setLightbox(false)}
        >
          <img src={main.url} alt={main.label} className="max-w-full max-h-full object-contain" onClick={(e) => e.stopPropagation()} />
          <button
            type="button"
            onClick={() => setLightbox(false)}
            className="absolute top-4 right-4 text-white text-2xl hover:text-slate-300"
            aria-label="닫기"
          >
            ✕
          </button>
          {total > 1 && (
            <>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); setSelected((s) => (s - 1 + total) % total); }}
                className="absolute left-4 text-white text-3xl px-3 py-2 hover:bg-white/10 rounded"
                aria-label="이전"
              >‹</button>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); setSelected((s) => (s + 1) % total); }}
                className="absolute right-4 text-white text-3xl px-3 py-2 hover:bg-white/10 rounded"
                aria-label="다음"
              >›</button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
