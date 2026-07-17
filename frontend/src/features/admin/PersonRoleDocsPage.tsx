import { useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import { ALL_PERSON_ROLES, PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';

type Requirement = 'REQUIRED' | 'OPTIONAL' | 'NONE';
/** /api/admin/person-roles/{role}/documents — 전체 활성 PERSON 서류 + 이 역할에 대한 3상태. */
type DocRow = { document_type_id: number; name: string; has_expiry: boolean; requirement: Requirement };

const REQ_LABEL: Record<Requirement, string> = { REQUIRED: '필수', OPTIONAL: '선택', NONE: '해당없음' };
const REQ_ORDER: Requirement[] = ['REQUIRED', 'OPTIONAL', 'NONE'];

/** 인력 역할 × 서류 체크리스트 (EquipmentTypeDocsPage 의 인력 역할판 미러).
 *  역할은 PersonRole enum(고정) 이라 좌측 목록은 정적. */
export default function PersonRoleDocsPage() {
  const [selected, setSelected] = useState<PersonRole>(ALL_PERSON_ROLES[0]);
  const [rows, setRows] = useState<DocRow[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);

  useEffect(() => {
    setLoadingRows(true);
    api.get<DocRow[]>(`/api/admin/person-roles/${selected}/documents`)
      .then((r) => setRows(r.data))
      .catch(() => toast.error('서류 체크리스트를 불러올 수 없습니다'))
      .finally(() => setLoadingRows(false));
  }, [selected]);

  /** 3상태 즉시 저장 + 로컬 반영. */
  async function setReq(docId: number, requirement: Requirement) {
    const prev = rows;
    setRows((cur) => cur.map((d) => (d.document_type_id === docId ? { ...d, requirement } : d)));
    try {
      await api.patch(`/api/admin/person-roles/${selected}/documents/${docId}`, { requirement });
    } catch (e: any) {
      setRows(prev); // 롤백
      toast.error(e?.response?.data?.message ?? '저장 실패');
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '인력역할 서류' }]}>
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-4">
        <div>
          <h1 className="text-2xl font-bold">인력역할 서류 체크리스트</h1>
          <p className="text-sm text-slate-500 mt-1">
            인력 역할을 고르고, 각 서류를 <b>필수 / 선택 / 해당없음</b>으로 지정합니다.
            지정한 필수/선택은 해당 역할 인력의 등록·서류 화면 체크리스트에 그대로 반영됩니다.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-[240px_1fr]">
          {/* 좌: 역할 목록 */}
          <div className="card p-0 overflow-hidden self-start">
            {ALL_PERSON_ROLES.map((r) => (
              <button key={r} type="button" onClick={() => setSelected(r)}
                className={`w-full px-3 py-2 text-left text-sm border-b border-slate-100 flex items-center gap-2 ${
                  selected === r ? 'bg-brand-50 text-brand-700 font-semibold' : 'hover:bg-slate-50 text-slate-700'
                }`}>
                <span className="truncate">{PERSON_ROLE_LABEL[r]}</span>
              </button>
            ))}
          </div>

          {/* 우: 선택된 역할의 서류 체크리스트 */}
          <div className="card p-0 overflow-hidden">
            <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 text-sm font-semibold text-slate-700">
              {PERSON_ROLE_LABEL[selected]} — 서류 ({rows.length})
            </div>
            {loadingRows ? (
              <div className="p-8 text-center text-slate-400">불러오는 중…</div>
            ) : rows.length === 0 ? (
              <div className="p-6 text-center text-sm text-slate-400">등록된 인력 서류종류가 없습니다</div>
            ) : (
              <div className="divide-y divide-slate-100">
                {rows.map((d) => (
                  <div key={d.document_type_id} className="px-4 py-3 flex flex-wrap items-center gap-x-3 gap-y-2">
                    <span className="font-medium text-slate-900 flex-1 min-w-[10rem] truncate">
                      {d.name}
                      {d.has_expiry && (
                        <span className="ml-2 text-[10px] px-1.5 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200 align-middle">만료관리</span>
                      )}
                    </span>
                    <div className="inline-flex rounded-lg border border-slate-300 overflow-hidden">
                      {REQ_ORDER.map((r) => {
                        const on = d.requirement === r;
                        const tone = r === 'REQUIRED' ? 'bg-rose-600' : r === 'OPTIONAL' ? 'bg-brand-600' : 'bg-slate-500';
                        return (
                          <button key={r} type="button" onClick={() => setReq(d.document_type_id, r)}
                            className={`px-3 py-1 text-xs font-semibold border-l first:border-l-0 border-slate-300 transition ${
                              on ? `${tone} text-white` : 'bg-white text-slate-500 hover:bg-slate-50'
                            }`}>
                            {REQ_LABEL[r]}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
