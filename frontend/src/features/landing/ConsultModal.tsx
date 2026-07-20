import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import PhoneInput from '../../components/forms/PhoneInput';
import { createConsultation } from '../../lib/consultation';

type Props = { onClose: () => void };

export default function ConsultModal({ onClose }: Props) {
  const [form, setForm] = useState({ companyName: '', contactName: '', phone: '', email: '', message: '' });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createConsultation({
        companyName: form.companyName,
        contactName: form.contactName,
        phone: form.phone,
        email: form.email || undefined,
        message: form.message,
      });
      setDone(true);
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '접수 실패');
      else setError('접수 실패');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4" onClick={onClose}>
      <div className="card w-full max-w-md" onClick={(e) => e.stopPropagation()}>
        {done ? (
          <div className="py-6 text-center">
            <div className="mb-3 text-3xl">✅</div>
            <h2 className="mb-1 text-lg font-bold text-slate-900">접수되었습니다</h2>
            <p className="mb-6 text-sm text-slate-500">담당자가 확인 후 연락드리겠습니다.</p>
            <button type="button" onClick={onClose} className="btn-primary w-full">닫기</button>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-lg font-bold text-slate-900">상담 요청</h2>
                <p className="mt-0.5 text-sm text-slate-500">도입 문의를 남겨주시면 연락드립니다.</p>
              </div>
              <button type="button" onClick={onClose} aria-label="닫기" className="text-slate-400 hover:text-slate-600">✕</button>
            </div>

            <label className="block">
              <span className="text-sm font-medium text-slate-700">업체명</span>
              <input value={form.companyName} onChange={(e) => update('companyName', e.target.value)} required className="input mt-1" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">담당자</span>
              <input value={form.contactName} onChange={(e) => update('contactName', e.target.value)} required className="input mt-1" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">연락처</span>
              <div className="mt-1"><PhoneInput value={form.phone} onChange={(v) => update('phone', v)} required /></div>
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">이메일 (선택)</span>
              <input type="email" value={form.email} onChange={(e) => update('email', e.target.value)} className="input mt-1" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">문의내용</span>
              <textarea value={form.message} onChange={(e) => update('message', e.target.value)} required rows={4} className="input mt-1" />
            </label>

            {error && (
              <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>
            )}

            <div className="flex gap-2">
              <button type="button" onClick={onClose} className="btn-ghost flex-1">취소</button>
              <button type="submit" disabled={submitting} className="btn-primary flex-1 disabled:opacity-50">
                {submitting ? '접수 중...' : '상담 요청'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
