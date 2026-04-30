import { formatBusinessNumber } from '../../lib/format';

type Props = {
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
  className?: string;
};

export default function BusinessNumberInput({ value, onChange, required, disabled, className }: Props) {
  return (
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(formatBusinessNumber(e.target.value))}
      required={required}
      disabled={disabled}
      placeholder="123-45-67890"
      inputMode="numeric"
      maxLength={12}
      className={className ?? 'input'}
    />
  );
}
