import { formatPhone } from '../../lib/format';

type Props = {
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
  className?: string;
};

export default function PhoneInput({ value, onChange, required, disabled, className }: Props) {
  return (
    <input
      type="tel"
      value={value}
      onChange={(e) => onChange(formatPhone(e.target.value))}
      required={required}
      disabled={disabled}
      placeholder="010-1234-5678"
      inputMode="numeric"
      maxLength={13}
      className={className ?? 'input'}
    />
  );
}
