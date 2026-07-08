import { useState } from 'react';
import { SCHEMA, type TemplateField } from '../../../../lib/worksheet/schema';
import type { PersonResponse } from '../../../../types/person';
import { api } from '../../../../lib/api';
import { SectionCard } from './SectionCard';

interface WorksheetSectionsProps {
  values: Record<string, any>;
  onChange: (key: string, val: any) => void;
  onAiRewrite?: (field: TemplateField) => void;
  persons: PersonResponse[];
}

/** S-9-F: AI 재작성 stub — /api/ai/rewrite 호출. 환경변수 미설정 시 503. */
async function defaultAiRewrite(
  field: TemplateField,
  current: any,
  apply: (key: string, v: any) => void
) {
  try {
    const res = await api.post<{ ok: boolean; value?: string; message?: string }>('/api/ai/rewrite', {
      text: typeof current === 'string' ? current : '',
      prompt: field.aiPrompt ?? `다음 항목 (${field.label}) 을 한 문장으로 자연스럽게 다시 써줘.`,
    });
    if (res.data.ok && res.data.value) {
      if (window.confirm(`AI 추천:\n\n${res.data.value}\n\n적용할까요?`)) {
        apply(field.key, res.data.value);
      }
    } else {
      alert(res.data.message ?? 'AI 재작성 실패');
    }
  } catch (e: any) {
    alert('AI 재작성 실패: ' + (e?.response?.data?.message || e?.message || e));
  }
}

/**
 * 워크시트 132 필드 (20섹션, p1~p5) 전체 렌더링.
 * 검색 + 필수섹션 / 세부섹션 분리 표시.
 */
export function WorksheetSections({
  values,
  onChange,
  onAiRewrite,
  persons,
}: WorksheetSectionsProps) {
  const [openSection, setOpenSection] = useState<string | null>('p1_site');
  const [filter, setFilter] = useState('');
  const [showDetail, setShowDetail] = useState(false);

  const essentialSections = SCHEMA.filter((s) => s.essential);
  const detailSections = SCHEMA.filter((s) => !s.essential);

  return (
    <div className="space-y-2">
      <div className="card p-3">
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="text-sm font-semibold text-slate-700">📝 워크시트 (132 필드 / 20 섹션)</div>
          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="필드/섹션 검색"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="text-xs border border-slate-300 rounded px-2 py-1 bg-white w-48"
            />
            <button
              type="button"
              onClick={() => setShowDetail((v) => !v)}
              className="text-xs px-2 py-1 rounded border border-slate-300 hover:bg-slate-50"
            >
              세부 설정 {showDetail ? '접기 ▴' : '펼치기 ▾'}
            </button>
          </div>
        </div>
      </div>

      <div className="space-y-1.5">
        {essentialSections.map((sec) => (
          <SectionCard
            key={sec.id}
            section={sec}
            values={values}
            onChange={onChange}
            onAiRewrite={onAiRewrite ?? ((field) => defaultAiRewrite(field, values[field.key], onChange))}
            open={openSection === sec.id}
            onToggle={() => setOpenSection(openSection === sec.id ? null : sec.id)}
            filter={filter}
            persons={persons}
          />
        ))}
      </div>

      {showDetail && (
        <div className="space-y-1.5 mt-3">
          <div className="text-xs font-semibold text-slate-500 px-1">— 선택 / 세부 항목 —</div>
          {detailSections.map((sec) => (
            <SectionCard
              key={sec.id}
              section={sec}
              values={values}
              onChange={onChange}
              onAiRewrite={onAiRewrite ?? ((field) => defaultAiRewrite(field, values[field.key], onChange))}
              open={openSection === sec.id}
              onToggle={() => setOpenSection(openSection === sec.id ? null : sec.id)}
              filter={filter}
              persons={persons}
            />
          ))}
        </div>
      )}
    </div>
  );
}
