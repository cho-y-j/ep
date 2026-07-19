import type { ReactNode } from 'react';

type Props = {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
};

/**
 * 필터 그룹 pill(토글 버튼). 단일 그룹 선택 UI — 알림 유형·상태 칩 등.
 * 사용법: <GroupPill active={key === 'all'} onClick={() => setKey('all')}>전체</GroupPill>
 */
export default function GroupPill({ active, onClick, children }: Props) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1.5 rounded-full text-sm font-semibold border transition ${
        active
          ? 'bg-brand-600 text-white border-brand-600'
          : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
      }`}
    >
      {children}
    </button>
  );
}
