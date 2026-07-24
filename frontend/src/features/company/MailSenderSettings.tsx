import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';

/** 발송 메일 계정 상태 — 비밀번호는 절대 내려오지 않는다(등록 여부·이메일·표시명만). */
type MailSenderState = { configured: boolean; email: string | null; name: string | null };

/**
 * 발송 메일 계정 설정 — 서류 심사 메일을 본인 메일(네이버 SMTP 등)로 보내기 위한 등록/해제.
 * 미등록이면 시스템 기본 계정으로 발송되며, 받는 분에게는 보낸사람 표시명(담당자·회사)이 표기된다.
 */
export default function MailSenderSettings() {
  const [state, setState] = useState<MailSenderState | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);

  function apply(s: MailSenderState) {
    setState(s);
    setEmail(s.email ?? '');
    setName(s.name ?? '');
    setPassword('');
  }

  useEffect(() => {
    api.get<MailSenderState>('/api/me/mail-sender')
      .then((r) => apply(r.data))
      .catch(() => setState({ configured: false, email: null, name: null }));
  }, []);

  async function save() {
    if (!email.trim()) { toast.error('발송할 이메일 주소를 입력하세요'); return; }
    if (!password) { toast.error('앱 비밀번호를 입력하세요'); return; }
    setSaving(true);
    try {
      const r = await api.put<MailSenderState>('/api/me/mail-sender', {
        email: email.trim(), password, name: name.trim() || undefined,
      });
      apply(r.data);
      toast.success('발송 메일 계정을 저장했습니다');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setSaving(false);
    }
  }

  async function clear() {
    setSaving(true);
    try {
      const r = await api.put<MailSenderState>('/api/me/mail-sender', { email: '' });
      apply(r.data);
      toast.success('발송 메일 계정을 해제했습니다. 이제 기본 계정으로 발송됩니다');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '해제 실패');
    } finally {
      setSaving(false);
    }
  }

  const configured = !!state?.configured;

  return (
    <section className="card space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-slate-900">발송 메일 계정</h2>
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
          configured ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'
        }`}>
          {configured ? '등록됨' : '기본 계정 사용 중'}
        </span>
      </div>
      <p className="text-sm text-slate-500">
        서류 심사 메일을 <b>내 메일 주소</b>로 보냅니다. 등록하지 않으면 시스템 기본 계정으로 발송되며,
        받는 분에게는 <b>내 이름과 회사</b>가 보낸사람으로 표시됩니다.
      </p>

      {configured && (
        <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-3 py-2 text-sm text-emerald-800">
          현재 <b>{state?.email}</b>{state?.name ? ` (${state.name})` : ''} 계정으로 발송됩니다.
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">발송 이메일</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                 placeholder="mycompany@naver.com" autoComplete="off" className="input w-full mt-1" />
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">보낸사람 표시명 (선택)</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)}
                 placeholder="예: OO중기 홍길동" className="input w-full mt-1" />
        </label>
        <label className="block md:col-span-2">
          <span className="text-xs font-semibold text-slate-500">앱 비밀번호</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 placeholder={configured ? '변경하려면 다시 입력하세요' : '네이버 앱 비밀번호 16자리'}
                 autoComplete="new-password" className="input w-full mt-1" />
          <span className="text-xs text-slate-400 mt-1 block">
            로그인 비밀번호가 아니라 <b>앱(SMTP) 전용 비밀번호</b>입니다. 저장 시 암호화되며 다시 표시되지 않습니다.
          </span>
        </label>
      </div>

      <div className="flex gap-2">
        <button type="button" onClick={save} disabled={saving}
                className="px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50">
          {saving ? '저장 중…' : '저장'}
        </button>
        {configured && (
          <button type="button" onClick={clear} disabled={saving}
                  className="px-4 py-2 rounded-lg bg-slate-100 text-slate-700 text-sm font-semibold hover:bg-slate-200 disabled:opacity-50">
            해제 (기본 계정 사용)
          </button>
        )}
      </div>

      {/* 네이버 앱 비밀번호 발급 안내 (접이식) */}
      <div className="border-t border-slate-100 pt-3">
        <button type="button" onClick={() => setGuideOpen((v) => !v)}
                className="text-sm font-medium text-slate-500 hover:text-slate-700">
          {guideOpen ? '▾' : '▸'} 네이버 앱 비밀번호 발급 방법
        </button>
        {guideOpen && (
          <ol className="mt-2 ml-1 space-y-1.5 text-sm text-slate-600 list-decimal list-inside">
            <li>네이버 로그인 → <b>내정보 → 보안설정</b>에서 <b>2단계 인증</b>을 켭니다.</li>
            <li>같은 화면의 <b>애플리케이션 비밀번호</b> 관리로 이동합니다.</li>
            <li>용도에 <b>메일(SMTP)</b>을 선택하고 이름(예: 스켑 서류발송)을 입력해 생성합니다.</li>
            <li>표시된 <b>16자리 비밀번호</b>를 위 <b>앱 비밀번호</b> 칸에 붙여넣고 저장합니다.</li>
            <li>네이버 메일 → <b>환경설정 → POP3/IMAP 설정</b>에서 <b>SMTP 사용</b>이 켜져 있어야 합니다.</li>
          </ol>
        )}
      </div>
    </section>
  );
}
