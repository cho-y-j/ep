import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

export type EquipmentFieldValues = {
  supplierId: number | '';
  vehicleNo: string;
  category: EquipmentCategory;
  model: string;
  manufacturer: string;
  year: string;
  isExternal: boolean;
  vehicleOwnerName: string;
  vehicleOwnerBusinessNo: string;
};

type Props = {
  values: EquipmentFieldValues;
  onChange: (next: EquipmentFieldValues) => void;
  /** ADMIN 등 supplier_id를 직접 골라야 할 때 회사 목록 전달. supplier가 셀프 등록할 땐 생략 */
  equipmentSuppliers?: CompanyResponse[];
  required?: boolean;
  disabled?: boolean;
};

/**
 * 장비 등록/수정 시 공통으로 쓰는 필드 묶음.
 * - 장비공급사가 자기 회사 장비 등록 (supplierId 자동 = 본인 회사)
 * - ADMIN이 특정 공급사의 장비를 등록 (supplierId dropdown으로 선택)
 * - 같은 컴포넌트로 양쪽 다 처리.
 */
export default function EquipmentFields({ values, onChange, equipmentSuppliers, required, disabled }: Props) {
  function patch(partial: Partial<EquipmentFieldValues>) {
    onChange({ ...values, ...partial });
  }

  return (
    <div className="space-y-4">
      {equipmentSuppliers && (
        <label className="block">
          <span className="text-sm font-medium text-slate-700">장비공급사 <span className="text-xs text-rose-600 font-semibold">(필수)</span></span>
          <select
            value={values.supplierId}
            onChange={(e) => patch({ supplierId: e.target.value === '' ? '' : Number(e.target.value) })}
            required={required}
            disabled={disabled}
            className="input mt-1 bg-white"
          >
            <option value="">— 선택 —</option>
            {equipmentSuppliers.map((c) => (
              <option key={c.id} value={c.id}>{c.name} ({c.business_number})</option>
            ))}
          </select>
        </label>
      )}

      <div className="block">
        <span className="text-sm font-medium text-slate-700">장비 출처</span>
        <label className={`mt-1 flex items-center gap-2.5 rounded-lg border px-3 py-2.5 select-none ${disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:bg-slate-50'} ${values.isExternal ? 'border-amber-300 bg-amber-50/40' : 'border-slate-200'}`}>
          <input
            type="checkbox"
            checked={!values.isExternal}
            onChange={(e) => patch({ isExternal: !e.target.checked })}
            disabled={disabled}
            className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
          />
          <span className="text-sm font-semibold text-slate-800">우리 장비</span>
          <span className="text-xs text-slate-400">— 체크 해제 시 외부 조달 장비</span>
        </label>
      </div>

      {values.isExternal && (
        <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50/40 p-3">
          <p className="text-xs text-amber-700">외부에서 조달한 장비입니다. 소유주(사업자) 정보를 입력하세요. <strong>사업자등록증</strong>은 장비 등록 후 상세 화면의 '서류 추가'로 등록할 수 있습니다. 사진(파일)이 없으면 나중에 추가해도 됩니다.</p>
          <label className="block">
            <span className="text-sm font-medium text-slate-700">소유주(사업자)명</span>
            <input type="text" value={values.vehicleOwnerName}
              onChange={(e) => patch({ vehicleOwnerName: e.target.value })} disabled={disabled}
              placeholder="○○건설기계" className="input mt-1" />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-slate-700">사업자등록번호</span>
            <input type="text" value={values.vehicleOwnerBusinessNo}
              onChange={(e) => patch({ vehicleOwnerBusinessNo: e.target.value })} disabled={disabled}
              placeholder="123-45-67890" className="input mt-1" />
          </label>
        </div>
      )}

      <label className="block">
        <span className="text-sm font-medium text-slate-700">장비 종류 <span className="text-xs text-rose-600 font-semibold">(필수)</span></span>
        <select
          value={values.category}
          onChange={(e) => patch({ category: e.target.value as EquipmentCategory })}
          required={required}
          disabled={disabled}
          className="input mt-1 bg-white"
        >
          {EQUIPMENT_CATEGORIES.map((c) => (
            <option key={c} value={c}>{EQUIPMENT_CATEGORY_LABEL[c]}</option>
          ))}
        </select>
      </label>

      <label className="block">
        <span className="text-sm font-medium text-slate-700">차량번호 <span className="text-xs text-rose-600 font-semibold">(필수)</span></span>
        <input
          type="text"
          value={values.vehicleNo}
          onChange={(e) => patch({ vehicleNo: e.target.value })}
          required={required}
          disabled={disabled}
          placeholder="경기99사1234"
          className="input mt-1"
        />
        <span className="mt-1 block text-xs text-slate-400">부속 장비처럼 차량번호가 없는 경우 임의 식별번호를 입력하세요.</span>
      </label>

      <div className="grid grid-cols-2 gap-3">
        <label className="block">
          <span className="text-sm font-medium text-slate-700">제조사 <span className="text-xs text-slate-400 font-semibold">(선택)</span></span>
          <input
            type="text"
            value={values.manufacturer}
            onChange={(e) => patch({ manufacturer: e.target.value })}
            disabled={disabled}
            placeholder="두산인프라코어"
            className="input mt-1"
          />
        </label>
        <label className="block">
          <span className="text-sm font-medium text-slate-700">제조년도 <span className="text-xs text-slate-400 font-semibold">(선택)</span></span>
          <input
            type="number"
            min={1900}
            max={2099}
            value={values.year}
            onChange={(e) => patch({ year: e.target.value })}
            disabled={disabled}
            placeholder="2022"
            className="input mt-1"
          />
        </label>
      </div>

      <label className="block">
        <span className="text-sm font-medium text-slate-700">모델명 <span className="text-xs text-slate-400 font-semibold">(선택)</span></span>
        <input
          type="text"
          value={values.model}
          onChange={(e) => patch({ model: e.target.value })}
          disabled={disabled}
          placeholder="DX300LCA"
          className="input mt-1"
        />
      </label>
    </div>
  );
}

export const EMPTY_EQUIPMENT_FIELDS: EquipmentFieldValues = {
  supplierId: '',
  vehicleNo: '',
  category: 'EXCAVATOR',
  model: '',
  manufacturer: '',
  year: '',
  isExternal: false,
  vehicleOwnerName: '',
  vehicleOwnerBusinessNo: '',
};
