import { useMemo } from 'react';
import { SITE_STATUS_LABEL } from '../../../../types/site';
import type { WorkPlanCreateState } from '../hooks/useWorkPlanCreate';

interface Step1Props {
  state: WorkPlanCreateState;
}

/** Step 1: BP 선택 → 그 BP 의 현장 → 작업 메타(제목/일자/시간/위치/설명). BP 로그인은 자기 회사 자동 고정. */
export function Step1SiteAndBp({ state }: Step1Props) {
  const { sitesForBp, siteId, setSiteId, bpCompanies, bpCompanyId, setBpCompanyId, isAdminMode } = state;
  const selectedBpName = useMemo(
    () => (isAdminMode
      ? bpCompanies.find((c) => c.id === bpCompanyId)?.name
      : sitesForBp[0]?.bp_company_name),
    [isAdminMode, bpCompanies, bpCompanyId, sitesForBp]
  );

  return (
    <div id="step-site" className="card p-4 bg-white border-slate-200 space-y-3">
      <div className="text-sm font-bold text-slate-900">기본 정보</div>

      <div className="grid grid-cols-12 gap-3">
        <div className="col-span-12 md:col-span-3">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">BP사 <span className="text-rose-500">*</span></label>
          {isAdminMode ? (
            <select
              value={bpCompanyId ?? ''}
              onChange={(e) => setBpCompanyId(e.target.value ? Number(e.target.value) : null)}
              className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
            >
              <option value="">-- BP사 선택 --</option>
              {bpCompanies.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              value={selectedBpName ?? '내 회사'}
              readOnly
              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm bg-slate-50 text-slate-700"
            />
          )}
        </div>
        <div className="col-span-12 md:col-span-4">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">현장 (선택)</label>
          <select
            value={siteId ?? ''}
            onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : null)}
            disabled={!bpCompanyId}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white disabled:bg-slate-50 disabled:text-slate-400"
          >
            <option value="">{bpCompanyId ? '-- 미정 (작업 위치만 자유 입력) --' : '-- BP사를 먼저 선택 --'}</option>
            {sitesForBp.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} · {SITE_STATUS_LABEL[s.status]}
                {s.code ? ` · ${s.code}` : ''}
              </option>
            ))}
          </select>
          <div className="text-[10px] text-slate-500 mt-0.5">현장은 나중에 확정 가능. 비우면 아래 "작업 위치"에 자유 텍스트로 적어주세요.</div>
        </div>
        <div className="col-span-12 md:col-span-5">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">제목 <span className="text-rose-500">*</span></label>
          <input
            type="text"
            value={state.title}
            onChange={(e) => state.setTitle(e.target.value)}
            placeholder="예: 2026-05-08 1차 고소작업"
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>

        <div className="col-span-6 md:col-span-2">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">작업 시작일 <span className="text-rose-500">*</span></label>
          <input
            type="date"
            value={state.workDate}
            onChange={(e) => state.setWorkDate(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>
        <div className="col-span-6 md:col-span-2">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">작업 종료일</label>
          <input
            type="date"
            value={state.workEndDate}
            onChange={(e) => state.setWorkEndDate(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>
        <div className="col-span-6 md:col-span-2">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">시작 시간</label>
          <input
            type="time"
            value={state.startTime}
            onChange={(e) => state.setStartTime(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>
        <div className="col-span-6 md:col-span-2">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">종료 시간</label>
          <input
            type="time"
            value={state.endTime}
            onChange={(e) => state.setEndTime(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>
        <div className="col-span-12 md:col-span-4">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">작업 위치 (현장 내 위치)</label>
          <input
            type="text"
            value={state.workLocation}
            onChange={(e) => state.setWorkLocation(e.target.value)}
            placeholder="예: A동 3층 외벽"
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>

        <div className="col-span-12">
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">작업 설명</label>
          <textarea
            value={state.description}
            onChange={(e) => state.setDescription(e.target.value)}
            rows={2}
            placeholder="작업 내용 / 주의사항 등"
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          />
        </div>
      </div>
    </div>
  );
}
