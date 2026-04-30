import { ALL_PERSON_ROLES, PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';

type Props = {
  value: PersonRole | '';
  onChange: (value: PersonRole | '') => void;
};

export default function PersonRoleFilter({ value, onChange }: Props) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-slate-500">역할</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as PersonRole | '')}
        className="input bg-white max-w-xs"
      >
        <option value="">전체</option>
        {ALL_PERSON_ROLES.map((r) => (
          <option key={r} value={r}>{PERSON_ROLE_LABEL[r]}</option>
        ))}
      </select>
    </div>
  );
}
