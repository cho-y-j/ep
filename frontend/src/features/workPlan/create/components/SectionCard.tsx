import type { TemplateField, TemplateSection } from '../../../../lib/worksheet/schema';
import type { PersonResponse } from '../../../../types/person';
import { FieldInput } from './FieldInput';
import { SignatureTable } from './SignatureTable';

interface SectionCardProps {
  section: TemplateSection;
  values: Record<string, any>;
  onChange: (key: string, val: any) => void;
  onAiRewrite?: (field: TemplateField) => void;
  open: boolean;
  onToggle: () => void;
  filter?: string;
  persons?: PersonResponse[];
}

/** 워크시트 schema 의 단일 섹션 — 헤더 클릭 토글 + grid-cols-2 필드. */
export function SectionCard({
  section,
  values,
  onChange,
  onAiRewrite,
  open,
  onToggle,
  filter,
  persons,
}: SectionCardProps) {
  const q = (filter || '').trim().toLowerCase();
  const fields = q
    ? section.fields.filter(
        (f) => f.label.toLowerCase().includes(q) || f.key.toLowerCase().includes(q)
      )
    : section.fields;
  const titleMatch = q && section.title.toLowerCase().includes(q);

  const highlight = (text: string) => {
    if (!q) return text;
    const i = text.toLowerCase().indexOf(q);
    if (i < 0) return text;
    return (
      <>
        {text.slice(0, i)}
        <mark className="bg-yellow-200 rounded px-0.5">{text.slice(i, i + q.length)}</mark>
        {text.slice(i + q.length)}
      </>
    );
  };

  return (
    <section className="card">
      <button
        type="button"
        onClick={onToggle}
        className="w-full px-3 py-2 flex items-center justify-between hover:bg-slate-50 text-left text-sm"
      >
        <span>
          <span className="text-xs font-mono text-slate-400 mr-1.5">{section.page.toUpperCase()}</span>
          <span className="font-semibold text-slate-800">
            {titleMatch ? highlight(section.title) : section.title}
          </span>
          {section.aiRewritable && (
            <span className="badge bg-purple-100 text-purple-700 ml-1.5 text-[10px]">AI</span>
          )}
          {q && (
            <span className="text-[10px] text-slate-400 ml-1.5">
              ({fields.length}/{section.fields.length})
            </span>
          )}
        </span>
        <span className="text-slate-400">{open ? '▾' : '▸'}</span>
      </button>
      {open && fields.length > 0 && section.id === 'p1_signatures' && (
        <div className="px-3 pb-3 border-t border-slate-100 pt-2">
          <SignatureTable values={values} onChange={onChange} persons={persons || []} />
        </div>
      )}
      {open && fields.length > 0 && section.id !== 'p1_signatures' && (
        <div className="px-3 pb-3 grid grid-cols-2 gap-1.5 border-t border-slate-100 pt-2">
          {fields.map((f) => (
            <FieldInput
              key={f.key}
              field={f}
              value={values[f.key]}
              onChange={onChange}
              onAiRewrite={onAiRewrite}
              highlight={highlight}
            />
          ))}
        </div>
      )}
    </section>
  );
}
