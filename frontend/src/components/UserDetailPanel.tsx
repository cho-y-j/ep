import { useState } from 'react';
import { AxiosError } from 'axios';
import SidePanel from './SidePanel';
import ConfirmDialog from './ConfirmDialog';
import { api } from '../lib/api';
import { ROLE_LABEL, type UserResponse } from '../types/auth';
import { useAuth } from '../contexts/AuthContext';

type Props = {
  user: UserResponse | null;
  onClose: () => void;
  onChange: (updated: UserResponse) => void;
};

type ActionKind = 'enable' | 'disable' | null;

export default function UserDetailPanel({ user, onClose, onChange }: Props) {
  const [pendingAction, setPendingAction] = useState<ActionKind>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user: me } = useAuth();

  if (!user) {
    return <SidePanel open={false} onClose={onClose} title="">{null}</SidePanel>;
  }

  const isSelf = me?.id === user.id;

  async function execute(kind: 'enable' | 'disable') {
    if (!user) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.patch<UserResponse>(`/api/users/${user.id}/${kind}`);
      onChange(res.data);
      setPendingAction(null);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '처리 실패');
      } else {
        setError('처리 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <SidePanel
        open={!!user}
        onClose={onClose}
        title="사용자 상세"
        footer={
          <div className="flex justify-end gap-2">
            {!user.enabled && (
              <button
                type="button"
                onClick={() => setPendingAction('enable')}
                className="btn-primary"
              >
                승인 (활성화)
              </button>
            )}
            {user.enabled && !isSelf && (
              <button
                type="button"
                onClick={() => setPendingAction('disable')}
                className="px-4 py-2 rounded-lg bg-red-600 text-white font-medium hover:bg-red-700"
              >
                비활성화
              </button>
            )}
            {user.enabled && isSelf && (
              <span className="text-xs text-slate-400 self-center">본인 계정은 비활성화할 수 없습니다</span>
            )}
          </div>
        }
      >
        <dl className="space-y-4 text-sm">
          <Row label="이메일" value={user.email} />
          <Row label="이름" value={user.name} />
          <Row label="휴대폰" value={user.phone ?? '—'} />
          <Row label="역할" value={ROLE_LABEL[user.role]} />
          <Row
            label="상태"
            value={
              user.enabled ? (
                <span className="inline-flex px-2 py-0.5 rounded bg-green-100 text-green-700 text-xs font-medium">활성</span>
              ) : (
                <span className="inline-flex px-2 py-0.5 rounded bg-amber-100 text-amber-700 text-xs font-medium">승인 대기</span>
              )
            }
          />
          <Row label="회사 ID" value={user.company_id ?? '—'} />
          <Row label="회사 관리자" value={user.is_company_admin ? '예' : '아니오'} />
          <Row label="가입일" value={new Date(user.created_at).toLocaleString()} />

          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
          )}
        </dl>
      </SidePanel>

      <ConfirmDialog
        open={pendingAction === 'enable'}
        title="사용자 승인"
        message={`${user.name} (${user.email}) 계정을 활성화합니다.\n승인 후 로그인이 가능해집니다.`}
        confirmLabel="승인"
        variant="primary"
        busy={busy}
        onConfirm={() => execute('enable')}
        onCancel={() => setPendingAction(null)}
      />

      <ConfirmDialog
        open={pendingAction === 'disable'}
        title="사용자 비활성화"
        message={`${user.name} (${user.email}) 계정을 비활성화합니다.\n로그인이 차단되고 현재 세션도 종료됩니다.`}
        confirmLabel="비활성화"
        variant="danger"
        busy={busy}
        onConfirm={() => execute('disable')}
        onCancel={() => setPendingAction(null)}
      />
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start gap-4">
      <dt className="w-24 shrink-0 text-slate-500">{label}</dt>
      <dd className="flex-1 text-slate-900">{value}</dd>
    </div>
  );
}
