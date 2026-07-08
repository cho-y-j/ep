import { useCallback, useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import type { ReviewItemResponse } from '../../types/notification';

type Bucket = 'expired' | 'soon' | 'all';

function daysUntil(dateStr?: string | null): number | null {
  if (!dateStr) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const d = new Date(dateStr);
  d.setHours(0, 0, 0, 0); // 로컬 자정으로 정규화 — UTC 파싱 off-by-one 방지 (대시보드 위젯과 동일)
  return Math.round((d.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
}

const OWNER_TYPE_LABEL: Record<string, string> = {
  PERSON: '인원',
  EQUIPMENT: '장비',
  COMPANY: '회사',
};

export default function AdminExpiringDocumentsPage() {
  const [items, setItems] = useState<ReviewItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bucket, setBucket] = useState<Bucket>('all');

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    api.get<ReviewItemResponse[]>('/api/documents/expiring', { params: { days: 30 } })
      .then((res) => setItems(res.data))
      .catch((err) => {
        if (err instanceof AxiosError) setError(err.response?.data?.message ?? '불러오기 실패');
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const counts = useMemo(() => {
    let expired = 0, soon = 0;
    for (const it of items) {
      const d = daysUntil(it.expiry_date);
      if (d == null) continue;
      if (d < 0) expired++;
      else soon++;
    }
    return { expired, soon, all: items.length };
  }, [items]);

  const filtered = useMemo(() => {
    if (bucket === 'all') return items;
    return items.filter((it) => {
      const d = daysUntil(it.expiry_date);
      if (d == null) return false;
      return bucket === 'expired' ? d < 0 : d >= 0;
    });
  }, [items, bucket]);

  return (
    <AppShell breadcrumb={[{ label: '대시보드', to: '/admin/dashboard' }, { label: '만료 임박 서류' }]}>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">만료 임박 서류</h1>
          <p className="text-sm text-slate-500 mt-1">
            30일 이내 만료 예정이거나 이미 만료된 서류입니다. 자원 페이지로 이동해서 재업로드하세요.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <BucketTab label="전체" count={counts.all} active={bucket === 'all'} onClick={() => setBucket('all')} tone="brand" />
          <BucketTab label="이미 만료됨" count={counts.expired} active={bucket === 'expired'} onClick={() => setBucket('expired')} tone="rose" />
          <BucketTab label="만료 임박 (30일)" count={counts.soon} active={bucket === 'soon'} onClick={() => setBucket('soon')} tone="amber" />
        </div>

        <div className="rounded-xl border border-slate-200 bg-white overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr className="text-left text-slate-500 text-xs">
                <th className="px-4 py-3 font-medium">서류</th>
                <th className="px-4 py-3 font-medium">자원</th>
                <th className="px-4 py-3 font-medium">공급사</th>
                <th className="px-4 py-3 font-medium">만료일</th>
                <th className="px-4 py-3 font-medium">남은 일수</th>
                <th className="px-4 py-3 font-medium">액션</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan={6} className="px-4 py-12 text-center text-slate-400">불러오는 중...</td></tr>
              ) : error ? (
                <tr><td colSpan={6} className="px-4 py-12 text-center text-rose-600">{error}</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={6} className="px-4 py-12 text-center text-slate-400">해당 카테고리에 서류가 없습니다.</td></tr>
              ) : filtered.map((it) => {
                const d = daysUntil(it.expiry_date);
                const expired = d != null && d < 0;
                const ownerLink = it.owner_type === 'EQUIPMENT'
                  ? `/equipment/${it.owner_id}`
                  : it.owner_type === 'PERSON'
                    ? `/persons/${it.owner_id}`
                    : `/admin/companies/${it.owner_id}`;
                return (
                  <tr key={it.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-900">{it.document_type_name}</div>
                      <div className="text-xs text-slate-500 mt-0.5 truncate max-w-[220px]">{it.file_name}</div>
                    </td>
                    <td className="px-4 py-3">
                      <Link to={ownerLink} className="text-brand-700 hover:text-brand-800 font-medium">{it.owner_name}</Link>
                      <div className="text-xs text-slate-500 mt-0.5">{OWNER_TYPE_LABEL[it.owner_type] ?? it.owner_type} #{it.owner_id}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-700">{it.owner_supplier_name ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-700">{it.expiry_date ?? '-'}</td>
                    <td className="px-4 py-3">
                      {d == null ? (
                        <span className="text-slate-400">-</span>
                      ) : expired ? (
                        <span className="inline-flex px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-semibold">
                          {Math.abs(d)}일 지남
                        </span>
                      ) : (
                        <span className="inline-flex px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 text-xs font-semibold">
                          D-{d}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Link to={ownerLink}
                            className="text-xs px-2 py-1 rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                        재업로드하러 가기
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}

function BucketTab({ label, count, active, onClick, tone }: {
  label: string; count: number; active: boolean; onClick: () => void;
  tone: 'brand' | 'rose' | 'amber';
}) {
  const activeCls = tone === 'rose' ? 'bg-rose-600 text-white border-rose-600'
    : tone === 'amber' ? 'bg-amber-500 text-white border-amber-500'
    : 'bg-brand-600 text-white border-brand-600';
  const idleCls = tone === 'rose' ? 'bg-white text-rose-700 border-rose-200 hover:bg-rose-50'
    : tone === 'amber' ? 'bg-white text-amber-700 border-amber-200 hover:bg-amber-50'
    : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50';
  return (
    <button type="button" onClick={onClick}
      className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${active ? activeCls : idleCls}`}>
      {label} ({count})
    </button>
  );
}
