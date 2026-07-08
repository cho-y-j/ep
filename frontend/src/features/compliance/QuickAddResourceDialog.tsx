import { useNavigate } from 'react-router-dom';
import EquipmentCreateForm from '../equipment/EquipmentCreateForm';
import PersonCreateForm from '../person/PersonCreateForm';
import type { CompanyType } from '../../types/auth';

type Kind = 'EQUIPMENT' | 'PERSON';

type Props = {
  kind: Kind;
  selfSupplierType?: CompanyType;
  onClose: () => void;
};

const TITLE: Record<Kind, string> = {
  EQUIPMENT: '장비 추가',
  PERSON: '인원 추가',
};

export default function QuickAddResourceDialog({ kind, selfSupplierType, onClose }: Props) {
  const navigate = useNavigate();

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[92vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
          <h3 className="text-lg font-bold text-slate-900">{TITLE[kind]}</h3>
          <button onClick={onClose} className="text-slate-500 hover:text-slate-900 text-xl leading-none">×</button>
        </div>
        <div className="p-5">
          {kind === 'EQUIPMENT' ? (
            <EquipmentCreateForm
              onCreated={(e) => navigate(`/equipment/${e.id}`)}
              onCancel={onClose}
            />
          ) : (
            <PersonCreateForm
              selfSupplierType={selfSupplierType}
              onCreated={(p) => navigate(`/persons/${p.id}`)}
              onCancel={onClose}
            />
          )}
        </div>
      </div>
    </div>
  );
}
