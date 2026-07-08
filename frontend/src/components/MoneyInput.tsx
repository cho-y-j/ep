import { useId } from 'react';

interface Props {
  value: number | string;
  onChange: (next: number | '') => void;
  placeholder?: string;
  disabled?: boolean;
  readOnly?: boolean;
  className?: string;
  /** 입력값 아래에 한글 금액 표시. 기본 true. */
  showKorean?: boolean;
}

/** 숫자를 만/억 단위 한글 금액으로. 0 또는 음수는 빈 문자열. */
export function toKoreanAmount(n: number): string {
  if (!n || n <= 0 || !Number.isFinite(n)) return '';
  const units: Array<[number, string]> = [
    [1_0000_0000_0000, '조'],
    [1_0000_0000, '억'],
    [1_0000, '만'],
  ];
  const parts: string[] = [];
  let remain = Math.floor(n);
  for (const [base, name] of units) {
    if (remain >= base) {
      const q = Math.floor(remain / base);
      parts.push(`${q.toLocaleString('ko-KR')}${name}`);
      remain = remain % base;
    }
  }
  if (remain > 0) parts.push(remain.toLocaleString('ko-KR'));
  return parts.join(' ') + '원';
}

/** 가격 입력 — 표시는 1,111,111 콤마, 내부 값은 number. 입력 아래 한글 금액 표시. */
export default function MoneyInput({
  value, onChange, placeholder, disabled, readOnly, className, showKorean = true,
}: Props) {
  const id = useId();
  const num = value === '' || value == null ? null : Number(value);
  const display = num == null ? '' : num.toLocaleString('ko-KR');
  const korean = num != null && num > 0 ? toKoreanAmount(num) : '';
  return (
    <div>
      <input
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={display}
        placeholder={placeholder}
        disabled={disabled}
        readOnly={readOnly}
        className={(className ?? 'input') + (readOnly ? ' bg-slate-50 text-slate-700' : '')}
        onChange={(e) => {
          if (readOnly) return;
          const raw = e.target.value.replace(/[^0-9]/g, '');
          onChange(raw === '' ? '' : Number(raw));
        }}
      />
      {showKorean && korean && (
        <div className="text-[11px] text-slate-500 mt-0.5">{korean}</div>
      )}
    </div>
  );
}
