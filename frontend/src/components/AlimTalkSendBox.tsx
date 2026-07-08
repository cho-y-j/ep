import { useState } from 'react';

type Props = {
  /** 사람 대상이면 그 사람의 전화번호 — "등록번호 추가" 버튼으로 자동 입력. */
  personPhone?: string | null;
  /** 현재 수신번호 목록 (부모가 보유). */
  value: string[];
  onChange: (phones: string[]) => void;
  /** 안내 문구 override. */
  hint?: string;
};

const normalize = (s: string) => s.replace(/\s+/g, '').trim();

/**
 * 다온톡 알림톡 수신번호 입력 박스 (재사용).
 * 토글 ON → 수동 칩 입력 + 등록번호 자동 추가. OFF → value 비움(미발송).
 * 실제 발송은 부모가 제출 body 에 alimtalk_phones 로 담아 서버가 처리.
 */
export default function AlimTalkSendBox({ personPhone, value, onChange, hint }: Props) {
  const [enabled, setEnabled] = useState(false);
  const [input, setInput] = useState('');

  const toggle = (on: boolean) => {
    setEnabled(on);
    if (!on) onChange([]); // OFF → 미발송
  };

  const add = (raw: string) => {
    const p = normalize(raw);
    if (!p || value.includes(p)) return;
    onChange([...value, p]);
  };

  const remove = (p: string) => onChange(value.filter((x) => x !== p));

  const addManual = () => {
    add(input);
    setInput('');
  };

  const phoneCandidate = personPhone ? normalize(personPhone) : '';

  return (
    <div className="rounded border border-slate-200 bg-slate-50 p-3">
      <label className="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" checked={enabled} onChange={(e) => toggle(e.target.checked)} />
        <span className="text-xs font-semibold text-slate-700">카카오 알림톡으로도 보내기</span>
      </label>

      {enabled && (
        <div className="mt-2 space-y-2">
          <p className="text-[11px] text-slate-500">
            {hint ?? '수신번호로 알림톡(실패 시 SMS)이 발송됩니다. 번호를 직접 입력하거나 등록번호를 추가하세요.'}
          </p>

          {value.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {value.map((p) => (
                <span key={p} className="inline-flex items-center gap-1 rounded-full bg-white border border-slate-300 px-2 py-0.5 text-xs text-slate-700">
                  {p}
                  <button type="button" onClick={() => remove(p)} className="text-slate-400 hover:text-rose-500" aria-label="삭제">×</button>
                </span>
              ))}
            </div>
          )}

          <div className="flex gap-1.5">
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addManual(); } }}
              placeholder="010-0000-0000"
              inputMode="tel"
              className="flex-1 px-2.5 py-1.5 text-sm border border-slate-300 rounded"
            />
            <button type="button" onClick={addManual} className="px-2.5 py-1.5 text-sm rounded border border-slate-300 text-slate-700 hover:bg-slate-100">
              추가
            </button>
          </div>

          {phoneCandidate && !value.includes(phoneCandidate) && (
            <button type="button" onClick={() => add(phoneCandidate)} className="text-xs text-sky-600 hover:underline">
              + 등록번호 추가 ({personPhone})
            </button>
          )}
        </div>
      )}
    </div>
  );
}
