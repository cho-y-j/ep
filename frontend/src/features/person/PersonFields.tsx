import PhoneInput from '../../components/forms/PhoneInput';
import type { CompanyResponse, CompanyType } from '../../types/auth';
import { PERSON_ROLE_LABEL, rolesAllowedFor, type PersonRole } from '../../types/person';

export type PersonFieldValues = {
  supplierId: number | '';
  name: string;
  birth: string;
  phone: string;
  roles: PersonRole[];
};

type Props = {
  values: PersonFieldValues;
  onChange: (next: PersonFieldValues) => void;
  /** ADMIN 컨텍스트일 때 회사 목록 (EQUIPMENT + MANPOWER 만). 자기 회사 셀프 등록은 생략 */
  suppliers?: CompanyResponse[];
  /** supplier 회사 type (역할 dropdown 필터링) */
  supplierType?: CompanyType;
  required?: boolean;
  disabled?: boolean;
};

export const EMPTY_PERSON_FIELDS: PersonFieldValues = {
  supplierId: '',
  name: '',
  birth: '',
  phone: '',
  roles: [],
};

export default function PersonFields({ values, onChange, suppliers, supplierType, required, disabled }: Props) {
  function patch(partial: Partial<PersonFieldValues>) {
    onChange({ ...values, ...partial });
  }

  function toggleRole(role: PersonRole) {
    if (values.roles.includes(role)) {
      patch({ roles: values.roles.filter((r) => r !== role) });
    } else {
      patch({ roles: [...values.roles, role] });
    }
  }

  // ADMIN 컨텍스트에서 supplier 선택 시, 그 회사의 type을 추출
  const effectiveType: CompanyType | undefined = supplierType
    ?? suppliers?.find((c) => c.id === values.supplierId)?.type;

  const allowedRoles: PersonRole[] = effectiveType ? rolesAllowedFor(effectiveType) : [];

  return (
    <div className="space-y-4">
      {suppliers && (
        <label className="block">
          <span className="text-sm font-medium text-slate-700">소속 공급사</span>
          <select
            value={values.supplierId}
            onChange={(e) => {
              const id = e.target.value === '' ? '' : Number(e.target.value);
              const company = suppliers.find((c) => c.id === id);
              const newAllowed = company ? rolesAllowedFor(company.type) : [];
              // 회사 변경 시 허용되지 않는 role은 제거
              patch({
                supplierId: id,
                roles: values.roles.filter((r) => newAllowed.includes(r)),
              });
            }}
            required={required}
            disabled={disabled}
            className="input mt-1 bg-white"
          >
            <option value="">— 선택 —</option>
            {suppliers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.type === 'EQUIPMENT' ? '장비공급사' : '인력공급사'})
              </option>
            ))}
          </select>
        </label>
      )}

      <label className="block">
        <span className="text-sm font-medium text-slate-700">이름</span>
        <input
          type="text"
          value={values.name}
          onChange={(e) => patch({ name: e.target.value })}
          required={required}
          disabled={disabled}
          className="input mt-1"
        />
      </label>

      <div className="grid grid-cols-2 gap-3">
        <label className="block">
          <span className="text-sm font-medium text-slate-700">생년월일</span>
          <input
            type="date"
            value={values.birth}
            onChange={(e) => patch({ birth: e.target.value })}
            disabled={disabled}
            className="input mt-1"
          />
        </label>
        <label className="block">
          <span className="text-sm font-medium text-slate-700">휴대폰</span>
          <div className="mt-1">
            <PhoneInput value={values.phone} onChange={(v) => patch({ phone: v })} disabled={disabled} />
          </div>
        </label>
      </div>

      <fieldset>
        <legend className="text-sm font-medium text-slate-700 mb-2">
          역할 (다중 선택)
          {effectiveType && (
            <span className="ml-2 text-xs text-slate-500">
              {effectiveType === 'EQUIPMENT' ? '장비공급사 — 조종원만 가능' : '인력공급사 — 6개 역할 선택 가능'}
            </span>
          )}
        </legend>
        {allowedRoles.length === 0 ? (
          <p className="text-sm text-slate-400">먼저 공급사를 선택하세요</p>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {allowedRoles.map((r) => (
              <label key={r} className="flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 hover:bg-slate-50 cursor-pointer">
                <input
                  type="checkbox"
                  checked={values.roles.includes(r)}
                  onChange={() => toggleRole(r)}
                  disabled={disabled}
                  className="rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm">{PERSON_ROLE_LABEL[r]}</span>
              </label>
            ))}
          </div>
        )}
      </fieldset>
    </div>
  );
}
