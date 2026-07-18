import { COMPANY_TYPE_LABEL, type CompanyType } from '../../types/auth';
import BusinessNumberInput from '../../components/forms/BusinessNumberInput';

type Props = {
  values: {
    name: string;
    businessNumber: string;
    type?: CompanyType;
  };
  onChange: (next: { name: string; businessNumber: string; type?: CompanyType }) => void;
  /** ADMIN 등 직접 type을 고를 수 있을 때 true. 가입 흐름은 role에서 자동 매핑되므로 false. */
  showType?: boolean;
  required?: boolean;
  disabled?: boolean;
};

const TYPES: CompanyType[] = ['BP', 'EQUIPMENT', 'MANPOWER', 'SAFETY_INSPECTION'];

/**
 * 회사 등록 / 가입 시 회사 입력에서 공통으로 쓰는 필드 묶음.
 * ADMIN 회사 등록 / 자기가입 / (앞으로) 어드민이 사용자 대신 만드는 회사 모달 등 모든 곳에서 동일하게 사용.
 */
export default function CompanyFields({ values, onChange, showType, required, disabled }: Props) {
  function patch(partial: Partial<typeof values>) {
    onChange({ ...values, ...partial });
  }

  return (
    <div className="space-y-4">
      <label className="block">
        <span className="text-sm font-medium text-slate-700">회사명</span>
        <input
          type="text"
          value={values.name}
          onChange={(e) => patch({ name: e.target.value })}
          required={required}
          disabled={disabled}
          className="input mt-1"
        />
      </label>

      <label className="block">
        <span className="text-sm font-medium text-slate-700">사업자번호</span>
        <div className="mt-1">
          <BusinessNumberInput
            value={values.businessNumber}
            onChange={(v) => patch({ businessNumber: v })}
            required={required}
            disabled={disabled}
          />
        </div>
      </label>

      {showType && (
        <label className="block">
          <span className="text-sm font-medium text-slate-700">유형</span>
          <select
            value={values.type ?? 'BP'}
            onChange={(e) => patch({ type: e.target.value as CompanyType })}
            required={required}
            disabled={disabled}
            className="input mt-1 bg-white"
          >
            {TYPES.map((t) => (
              <option key={t} value={t}>{COMPANY_TYPE_LABEL[t]}</option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}
