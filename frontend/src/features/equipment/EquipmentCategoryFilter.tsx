import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';

type Props = {
  value: EquipmentCategory | '';
  onChange: (value: EquipmentCategory | '') => void;
};

export default function EquipmentCategoryFilter({ value, onChange }: Props) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-slate-500">분류</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as EquipmentCategory | '')}
        className="input bg-white max-w-xs"
      >
        <option value="">전체</option>
        {EQUIPMENT_CATEGORIES.map((c) => (
          <option key={c} value={c}>{EQUIPMENT_CATEGORY_LABEL[c]}</option>
        ))}
      </select>
    </div>
  );
}
