interface ActionBarProps {
  overall: number;
  missing: string[];
  saving: boolean;
  generating: boolean;
  saveMsg: string;
  syncMsg?: string;
  onSave: () => void;
  onDownloadDocx: () => void;
  onDownloadPdf: () => void;
  onOpenMail: () => void;
  onOpenEditor: () => void;
}

/** S-9-C: 하단 고정 액션바 — 진행률 + 저장 + DOCX/PDF/편집기/메일. */
export function ActionBar({
  overall,
  missing,
  saving,
  generating,
  saveMsg,
  syncMsg,
  onSave,
  onDownloadDocx,
  onDownloadPdf,
  onOpenMail,
  onOpenEditor,
}: ActionBarProps) {
  return (
    <div className="sticky bottom-0 -mx-4 px-4 py-3 bg-white border-t border-slate-200 flex gap-2 justify-between items-center shadow-lg flex-wrap">
      <div className="flex flex-col gap-0.5 min-w-0">
        <div className="text-xs text-slate-500 truncate">
          {overall === 100 ? '완료 · 모든 필수 항목 준비 완료' : `! 필수 누락: ${missing.join(', ')}`}
        </div>
        {saveMsg && (
          <div className={`text-[10px] truncate ${saveMsg.startsWith('✓') ? 'text-emerald-600' : 'text-rose-600'}`}>
            {saveMsg}
          </div>
        )}
        {syncMsg && (
          <div
            className={`text-[10px] truncate ${
              syncMsg.startsWith('✕') ? 'text-rose-600' : syncMsg.startsWith('✓') ? 'text-emerald-600' : 'text-blue-600'
            }`}
          >
            {syncMsg}
          </div>
        )}
      </div>
      <div className="flex gap-2 flex-wrap">
        <button
          type="button"
          onClick={onDownloadDocx}
          disabled={generating}
          className="px-3 py-1.5 rounded-md border border-slate-300 hover:bg-slate-50 text-sm disabled:opacity-50"
          title="현재 폼 + 첨부 서류로 워드 파일 생성"
        >
          DOCX
        </button>
        <button
          type="button"
          onClick={onDownloadPdf}
          disabled={generating}
          className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          PDF 다운로드
        </button>
        <button
          type="button"
          onClick={onOpenEditor}
          disabled={generating}
          className="px-3 py-1.5 rounded-md bg-violet-600 text-white text-sm font-medium hover:bg-violet-700 disabled:opacity-50"
          title="새 탭에서 OnlyOffice 미리보기/편집"
        >
          편집기 (새 탭)
        </button>
        <button
          type="button"
          onClick={onOpenMail}
          disabled={generating}
          className="px-3 py-1.5 rounded-md bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
        >
          PDF 메일
        </button>
        <button
          type="button"
          onClick={onSave}
          disabled={saving || overall < 100}
          className="px-3 py-1.5 rounded-md bg-slate-800 text-white text-sm font-medium hover:bg-slate-900 disabled:opacity-50"
          title={overall < 100 ? '필수 항목을 모두 입력하세요' : '작업계획서를 새로 생성합니다'}
        >
          {saving ? '저장 중...' : '작업계획서 생성'}
        </button>
      </div>
    </div>
  );
}
