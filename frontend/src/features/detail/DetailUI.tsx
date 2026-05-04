import Avatar from '../../components/Avatar';

export type HealthStatus = 'running' | 'attention' | 'broken' | 'working' | 'leave' | 'inactive';

const STATUS_STYLE: Record<HealthStatus, { label: string; className: string }> = {
  running: { label: '가동중', className: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  attention: { label: '점검필요', className: 'bg-amber-50 text-amber-700 ring-amber-200' },
  broken: { label: '고장', className: 'bg-rose-50 text-rose-700 ring-rose-200' },
  working: { label: '근무중', className: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  leave: { label: '휴무', className: 'bg-amber-50 text-amber-700 ring-amber-200' },
  inactive: { label: '비활성', className: 'bg-slate-100 text-slate-600 ring-slate-200' },
};

export type DetailTabKey = 'overview' | 'inspection' | 'operation' | 'location' | 'documents';

type DetailTab = {
  key: DetailTabKey;
  label: string;
  count?: number;
};

export const DETAIL_TABS: DetailTab[] = [
  { key: 'overview', label: '개요' },
  { key: 'inspection', label: '점검이력' },
  { key: 'operation', label: '가동이력' },
  { key: 'location', label: '위치이력' },
  { key: 'documents', label: '첨부서류' },
];

export function StatusBadge({ status }: { status: HealthStatus }) {
  const style = STATUS_STYLE[status];
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${style.className}`}>
      {style.label}
    </span>
  );
}

export function ProgressBar({ value, label }: { value: number; label?: string }) {
  const safeValue = Math.max(0, Math.min(100, value));
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        {label && <span className="font-medium text-slate-700">{label}</span>}
        <span className="font-semibold text-slate-900">{safeValue}%</span>
      </div>
      <div className="h-2.5 rounded-full bg-slate-100 overflow-hidden">
        <div
          className="h-full rounded-full bg-brand-600 transition-all"
          style={{ width: `${safeValue}%` }}
        />
      </div>
    </div>
  );
}

export function InfoField({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-slate-900 truncate">{value}</dd>
    </div>
  );
}

type PhotoGalleryProps = {
  fetchUrl?: string;
  hasPhoto: boolean;
  photoNonce: number;
  fallbackText: string;
  alt: string;
  canEdit: boolean;
  photoBusy: boolean;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onFile: (file: File) => void;
  onDelete: () => void;
  accentLabel: string;
};

export function PhotoGallery({
  fetchUrl,
  hasPhoto,
  photoNonce,
  fallbackText,
  alt,
  canEdit,
  photoBusy,
  fileInputRef,
  onFile,
  onDelete,
  accentLabel,
}: PhotoGalleryProps) {
  const thumbnails = [accentLabel, '전면', '측면', '서류'];

  return (
    <div className="space-y-3">
      <div className="aspect-[4/3] w-full overflow-hidden rounded-xl border border-slate-200 bg-slate-100 shadow-sm">
        {hasPhoto && fetchUrl ? (
          <Avatar
            key={photoNonce}
            fetchUrl={fetchUrl}
            fallbackText={fallbackText}
            alt={alt}
            size={360}
            rounded="lg"
            className="!w-full !h-full"
          />
        ) : (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={!canEdit || photoBusy}
            className="flex h-full w-full flex-col items-center justify-center gap-2 text-sm font-medium text-slate-500 hover:bg-slate-50 disabled:cursor-default disabled:hover:bg-slate-100"
          >
            <span className="text-3xl text-brand-500">+</span>
            <span>{canEdit ? '대표 이미지 추가' : '등록된 이미지 없음'}</span>
          </button>
        )}
      </div>

      <div className="grid grid-cols-4 gap-2">
        {thumbnails.map((name, index) => (
          <button
            key={name}
            type="button"
            className={`aspect-[4/3] overflow-hidden rounded-lg border text-xs font-semibold transition-colors ${
              index === 0
                ? 'border-brand-500 bg-brand-50 text-brand-700'
                : 'border-slate-200 bg-white text-slate-500 hover:border-brand-200'
            }`}
          >
            {index === 0 && hasPhoto && fetchUrl ? (
              <Avatar
                key={`${photoNonce}-thumb`}
                fetchUrl={fetchUrl}
                fallbackText={fallbackText}
                alt={alt}
                size={88}
                rounded="lg"
                className="!w-full !h-full"
              />
            ) : (
              <span className="flex h-full items-center justify-center">{name}</span>
            )}
          </button>
        ))}
      </div>

      {canEdit && (
        <div className="flex gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            hidden
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onFile(file);
              e.target.value = '';
            }}
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={photoBusy}
            className="flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
          >
            {photoBusy ? '업로드 중...' : hasPhoto ? '이미지 변경' : '이미지 추가'}
          </button>
          {hasPhoto && (
            <button
              type="button"
              onClick={onDelete}
              disabled={photoBusy}
              className="rounded-lg px-3 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50 disabled:opacity-50"
            >
              삭제
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export function DetailTabs({
  active,
  onChange,
  documentCount,
}: {
  active: DetailTabKey;
  onChange: (key: DetailTabKey) => void;
  documentCount?: number;
}) {
  return (
    <div className="border-b border-slate-200">
      <nav className="-mb-px flex gap-2 overflow-x-auto" aria-label="상세 탭">
        {DETAIL_TABS.map((tab) => {
          const count = tab.key === 'documents' ? documentCount : tab.count;
          const selected = active === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => onChange(tab.key)}
              className={`inline-flex items-center gap-2 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold transition-colors ${
                selected
                  ? 'border-brand-600 text-brand-700'
                  : 'border-transparent text-slate-500 hover:border-slate-200 hover:text-slate-900'
              }`}
            >
              {tab.label}
              {typeof count === 'number' && (
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{count}</span>
              )}
            </button>
          );
        })}
      </nav>
    </div>
  );
}

export function SummaryCard({
  title,
  children,
  action,
}: {
  title: string;
  children: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h3 className="text-base font-bold text-slate-900">{title}</h3>
        {action}
      </div>
      {children}
    </section>
  );
}

export function HistoryList({
  items,
}: {
  items: Array<{ title: string; meta: string; status?: HealthStatus; value?: string }>;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      {items.map((item, index) => (
        <div
          key={`${item.title}-${item.meta}`}
          className={`grid grid-cols-1 gap-3 px-5 py-4 text-sm md:grid-cols-[1fr_180px_120px] ${
            index > 0 ? 'border-t border-slate-100' : ''
          }`}
        >
          <div>
            <p className="font-semibold text-slate-900">{item.title}</p>
            <p className="mt-1 text-xs text-slate-500">{item.meta}</p>
          </div>
          <div className="text-slate-600">{item.value ?? '-'}</div>
          <div>{item.status && <StatusBadge status={item.status} />}</div>
        </div>
      ))}
    </div>
  );
}
