import { useId } from 'react';
import { type Box, type Parser, PARSERS, PARSER_LABEL } from './regionTemplateTypes';

type Props = {
  box: Box | null;
  /** datalist 제안 — 표준키 ∪ 그 doc-type required_fields (커스텀 입력도 허용). */
  keyOptions: string[];
  /** 이 박스 key 의 미리보기 추출 결과 (value=parser 적용값, rawText=원문, score=평균 신뢰도 0..1). */
  preview?: { value: string; rawText: string; score: number } | null;
  onChange: (patch: Partial<Box>) => void;
  onDelete: () => void;
};

const SCORE_WARN = 0.6;

/** 선택된 박스의 field key(datalist)·parser(select) 편집 + 미리보기 값/신뢰도 + 삭제. */
export default function FieldInspector({ box, keyOptions, preview, onChange, onDelete }: Props) {
  const listId = useId();

  if (!box) {
    return (
      <div className="p-4 text-sm text-slate-400">
        캔버스에서 박스를 그리거나 선택하세요.
      </div>
    );
  }

  const lowScore = preview != null && preview.score < SCORE_WARN;

  return (
    <div className="p-4 space-y-3">
      <label className="block">
        <span className="text-xs font-medium text-slate-600">필드 key</span>
        <input list={listId} value={box.key}
          onChange={(e) => onChange({ key: e.target.value })}
          placeholder="예: vehicle_no"
          className="input mt-1" />
        <datalist id={listId}>
          {keyOptions.map((k) => <option key={k} value={k} />)}
        </datalist>
        <p className="text-[11px] text-slate-400 mt-1">
          폼 자동채움이 되려면 이 서류의 required_fields 키와 일치해야 합니다.
        </p>
      </label>

      <label className="block">
        <span className="text-xs font-medium text-slate-600">parser</span>
        <select value={box.parser}
          onChange={(e) => onChange({ parser: e.target.value as Parser })}
          className="input mt-1">
          {PARSERS.map((p) => <option key={p} value={p}>{PARSER_LABEL[p]} ({p})</option>)}
        </select>
      </label>

      <div className="rounded border border-slate-200 bg-slate-50 p-2.5">
        <div className="text-[11px] font-semibold text-slate-500 mb-1">미리보기</div>
        {preview == null ? (
          <div className="text-xs text-slate-400">추출 대기…</div>
        ) : (
          <>
            <div className="text-sm font-medium text-slate-800 break-all">
              {preview.value || <span className="text-slate-400">(빈 값)</span>}
            </div>
            {preview.rawText && preview.rawText !== preview.value && (
              <div className="text-[11px] text-slate-400 break-all mt-0.5">원문: {preview.rawText}</div>
            )}
            <div className={`text-[11px] mt-1 font-medium ${lowScore ? 'text-amber-600' : 'text-emerald-600'}`}>
              신뢰도 {(preview.score * 100).toFixed(0)}%{lowScore ? ' — 박스를 재조정해 보세요' : ''}
            </div>
          </>
        )}
      </div>

      <button type="button" onClick={onDelete}
        className="text-sm px-3 py-1.5 rounded border border-rose-300 text-rose-600 hover:bg-rose-50">
        이 박스 삭제
      </button>
    </div>
  );
}
