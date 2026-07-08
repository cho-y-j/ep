import type { TemplateField } from '../../../../lib/worksheet/schema';

interface FieldInputProps {
  field: TemplateField;
  value: any;
  onChange: (key: string, val: any) => void;
  onAiRewrite?: (field: TemplateField) => void;
  highlight?: (text: string) => any;
}

/** SectionCard 안에서 단일 워크시트 필드를 렌더 — checkbox/textarea/date/text 자동 분기. */
export function FieldInput({ field, value, onChange, onAiRewrite, highlight }: FieldInputProps) {
  const label = highlight ? highlight(field.label) : field.label;

  if (field.type === 'checkbox') {
    return (
      <label className="col-span-1 flex items-center gap-1.5 p-1.5 border border-slate-200 rounded text-xs cursor-pointer hover:bg-slate-50">
        <input
          data-field-key={field.key}
          type="checkbox"
          checked={!!value}
          onChange={(e) => onChange(field.key, e.target.checked)}
        />
        <span className="truncate">{label}</span>
      </label>
    );
  }

  if (field.type === 'textarea') {
    return (
      <div className="col-span-2">
        <div className="flex justify-between items-center mb-0.5">
          <label className="text-[10px] font-medium text-slate-600">{label}</label>
          {field.aiPrompt && onAiRewrite && (
            <button
              type="button"
              onClick={() => onAiRewrite(field)}
              className="text-[10px] text-purple-600 hover:underline"
            >
              AI 재작성
            </button>
          )}
        </div>
        <textarea
          data-field-key={field.key}
          rows={2}
          className="w-full border border-slate-300 rounded px-2 py-1 text-xs"
          value={value || ''}
          onChange={(e) => onChange(field.key, e.target.value)}
        />
      </div>
    );
  }

  if (field.type === 'date') {
    return (
      <div>
        <label className="text-[10px] font-medium text-slate-600 block">{label}</label>
        <input
          data-field-key={field.key}
          type="date"
          className="w-full border border-slate-300 rounded px-2 py-1 text-xs"
          value={value || ''}
          onChange={(e) => onChange(field.key, e.target.value)}
        />
      </div>
    );
  }

  return (
    <div>
      <label className="text-[10px] font-medium text-slate-600 block">{label}</label>
      <input
        data-field-key={field.key}
        type="text"
        className="w-full border border-slate-300 rounded px-2 py-1 text-xs"
        value={value || ''}
        onChange={(e) => onChange(field.key, e.target.value)}
        placeholder={field.placeholder}
      />
    </div>
  );
}
