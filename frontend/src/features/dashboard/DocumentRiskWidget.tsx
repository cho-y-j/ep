import { Link } from 'react-router-dom';

export type DocumentRisk = {
  id: number;
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  owner_name: string;
  document_type_id: number;
  document_type_name: string;
  expiry_date: string | null;
  verification_status: string;
  risk: 'EXPIRED' | 'EXPIRING_SOON' | 'REJECTED' | 'OCR_REVIEW_REQUIRED';
};

const RISK_LABEL: Record<DocumentRisk['risk'], { label: string; cls: string }> = {
  EXPIRED:              { label: '만료됨',    cls: 'bg-rose-100 text-rose-800' },
  EXPIRING_SOON:        { label: '만료 임박', cls: 'bg-amber-100 text-amber-800' },
  REJECTED:             { label: '반려',      cls: 'bg-rose-100 text-rose-800' },
  OCR_REVIEW_REQUIRED:  { label: 'OCR 검토',  cls: 'bg-blue-100 text-blue-800' },
};

function daysUntil(date: string | null): number | null {
  if (!date) return null;
  const target = new Date(date);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  target.setHours(0, 0, 0, 0);
  return Math.round((target.getTime() - today.getTime()) / 86_400_000);
}

type Props = {
  id?: string;
  title?: string;
  items: DocumentRisk[];
};

/**
 * Dashboard 위험 서류 위젯. owner_name + 서류명 + 만료일 + 위험도.
 * 클릭하면 그 자원 상세 페이지로 이동.
 */
export default function DocumentRiskWidget({ id, title = '만료 임박 / 위험 서류', items }: Props) {
  return (
    <section id={id} className="card">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-bold text-slate-900">{title}</h2>
        <span className="text-xs text-slate-500">{items.length}건</span>
      </div>
      {items.length === 0 ? (
        <div className="text-xs text-slate-400 py-6 text-center border border-dashed border-slate-200 rounded">
          만료 임박 / 위험 서류가 없습니다
        </div>
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((r) => {
            const rl = RISK_LABEL[r.risk];
            const days = daysUntil(r.expiry_date);
            const href = r.owner_type === 'EQUIPMENT' ? `/equipment/${r.owner_id}` : `/persons/${r.owner_id}`;
            return (
              <li key={r.id} className="py-2">
                <Link to={href} className="flex items-center justify-between gap-3 hover:bg-slate-50 -mx-1 px-1 rounded">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[10px] px-1.5 py-0.5 rounded-full font-medium bg-slate-100 text-slate-600">
                        {r.owner_type === 'EQUIPMENT' ? '장비' : '인원'}
                      </span>
                      <span className="text-sm font-semibold text-slate-900 truncate">{r.owner_name}</span>
                    </div>
                    <div className="text-xs text-slate-500 mt-0.5 truncate">
                      {r.document_type_name}
                      {r.expiry_date && (
                        <>
                          {' · 만료 '}
                          {r.expiry_date}
                          {days != null && (
                            <span className={`ml-1 ${days < 0 ? 'text-rose-600 font-semibold' : days <= 7 ? 'text-amber-700 font-semibold' : 'text-slate-500'}`}>
                              ({days < 0 ? `${-days}일 경과` : `D-${days}`})
                            </span>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                  <span className={`shrink-0 text-[10px] px-1.5 py-0.5 rounded-full font-medium ${rl.cls}`}>
                    {rl.label}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
