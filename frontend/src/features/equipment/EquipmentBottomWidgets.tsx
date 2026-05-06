import { Link } from 'react-router-dom';
import Avatar from '../../components/Avatar';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentResponse } from '../../types/equipment';

type Props = {
  equipment: EquipmentResponse[];
};

export default function EquipmentBottomWidgets({ equipment }: Props) {
  const expiringSoon = equipment
    .filter((e) => e.expiring_count > 0 || (e.insurance_expiry && new Date(e.insurance_expiry).getTime() - Date.now() < 60 * 24 * 3600_000))
    .slice(0, 3);

  const broken = equipment.filter((e) => (e.utilization_pct ?? 0) === 0).slice(0, 3);

  const top5 = [...equipment]
    .sort((a, b) => (b.utilization_pct ?? 0) - (a.utilization_pct ?? 0))
    .slice(0, 5);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <Widget title="점검 만료 임박 장비">
        {expiringSoon.length === 0 ? (
          <Empty text="만료 임박 장비 없음" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {expiringSoon.map((e) => (
              <li key={e.id} className="py-3 flex items-center gap-3">
                <Avatar
                  fetchUrl={e.has_photo ? `/api/equipment/${e.id}/photo` : undefined}
                  fallbackText={e.vehicle_no || ''}
                  size={40}
                  rounded="lg"
                />
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold truncate">
                    {EQUIPMENT_CATEGORY_LABEL[e.category]} {e.vehicle_no ?? ''} <span className="text-xs text-slate-500">({e.code ?? '-'})</span>
                  </div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    다음 점검일 <span className="text-rose-600 font-semibold">2026.05.15 (D-5)</span>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Widget>

      <Widget title="고장 장비 현황">
        {broken.length === 0 ? (
          <Empty text="고장 장비 없음" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {broken.map((e) => (
              <li key={e.id} className="py-3 flex items-center gap-3">
                <Avatar
                  fetchUrl={e.has_photo ? `/api/equipment/${e.id}/photo` : undefined}
                  fallbackText={e.vehicle_no || ''}
                  size={40}
                  rounded="lg"
                />
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold truncate">
                    {EQUIPMENT_CATEGORY_LABEL[e.category]} {e.vehicle_no ?? ''} <span className="text-xs text-slate-500">({e.code ?? '-'})</span>
                  </div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    고장일 <span className="text-rose-600 font-semibold">2026.05.01 (3일 경과)</span>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Widget>

      <Widget title="장비 가동률 TOP 5">
        {top5.length === 0 ? (
          <Empty text="등록된 장비 없음" />
        ) : (
          <ul className="space-y-3">
            {top5.map((e, i) => {
              const util = e.utilization_pct ?? 0;
              return (
                <li key={e.id} className="flex items-center gap-3">
                  <span className="shrink-0 w-5 text-sm font-bold text-slate-400">{i + 1}</span>
                  <div className="flex-1 min-w-0">
                    <Link to={`/equipment/${e.id}`} className="text-sm hover:underline truncate block">
                      {EQUIPMENT_CATEGORY_LABEL[e.category]} {e.vehicle_no ?? ''} <span className="text-xs text-slate-500">({e.code ?? '-'})</span>
                    </Link>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <div className="w-24 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-blue-500" style={{ width: `${util}%` }} />
                    </div>
                    <span className="text-sm font-semibold text-slate-900 w-10 text-right">{util}%</span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Widget>
    </div>
  );
}

function Widget({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-base font-bold">{title}</h3>
        <button type="button" className="text-xs text-slate-500 hover:text-slate-900">더보기 ›</button>
      </div>
      {children}
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="text-sm text-slate-400 py-6 text-center">{text}</p>;
}
