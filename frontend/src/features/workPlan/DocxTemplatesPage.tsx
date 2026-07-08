import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import type { DocxTemplateResponse } from '../../types/docxTemplate';

/**
 * DOCX 템플릿 관리 — ADMIN 은 전역 + 모든 회사 템플릿, BP 는 자기 회사 + 전역(read).
 *
 * 지원 placeholder ({key} 문법):
 *   {title} {site_name} {bp_company_name} {work_date} {start_time} {end_time}
 *   {work_location} {description} {status} {equipment_list} {person_list}
 *   {equipment_count} {person_count} {printed_at}
 */
export default function DocxTemplatesPage() {
  const { user, company } = useAuth();
  const [items, setItems] = useState<DocxTemplateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [scope, setScope] = useState<'GLOBAL' | 'COMPANY'>(user?.role === 'ADMIN' ? 'GLOBAL' : 'COMPANY');
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  const isAdmin = user?.role === 'ADMIN';

  async function load() {
    setLoading(true);
    try {
      const res = await api.get<DocxTemplateResponse[]>('/api/docx-templates', { params: { targetType: 'WORK_PLAN' } });
      setItems(res.data);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); }, []);

  async function upload(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!file || !name.trim()) { setError('이름과 파일은 필수'); return; }
    setSaving(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const params: Record<string, string> = { targetType: 'WORK_PLAN', name: name.trim() };
      if (scope === 'COMPANY' && company?.id) params.companyId = String(company.id);
      // ADMIN 은 GLOBAL 가능 (companyId 비워서 전역)
      await api.post('/api/docx-templates', fd, {
        params,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setName('');
      setFile(null);
      void load();
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '업로드 실패');
    } finally {
      setSaving(false);
    }
  }

  async function rename(id: number, current: string) {
    const next = window.prompt('새 이름', current);
    if (!next || next.trim() === current) return;
    try {
      await api.patch(`/api/docx-templates/${id}`, { name: next.trim() });
      void load();
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '이름 변경 실패');
    }
  }

  async function remove(id: number, displayName: string) {
    if (!window.confirm(`"${displayName}" 템플릿을 삭제하시겠습니까?`)) return;
    try {
      await api.delete(`/api/docx-templates/${id}`);
      void load();
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '삭제 실패');
    }
  }

  async function download(id: number, displayName: string) {
    try {
      const res = await api.get(`/api/docx-templates/${id}/file`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${displayName}.docx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '다운로드 실패');
    }
  }

  return (
    <AppShell breadcrumb={[{ label: 'DOCX 템플릿' }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-950">작업계획서 DOCX 템플릿</h1>
          <p className="mt-1 text-sm text-slate-500">
            업로드한 DOCX 의 <code className="rounded bg-slate-100 px-1 text-xs">{'{key}'}</code> 자리에 작업계획서 데이터가 자동 채워집니다.
          </p>
        </div>

        <section className="card">
          <h2 className="text-base font-bold text-slate-900 mb-3">새 템플릿 업로드</h2>
          <form onSubmit={upload} className="grid gap-3 md:grid-cols-3">
            <label className="block md:col-span-1">
              <span className="text-xs font-semibold text-slate-500">템플릿 이름</span>
              <input value={name} onChange={(e) => setName(e.target.value)} required className="input mt-1" />
            </label>
            {isAdmin && (
              <label className="block md:col-span-1">
                <span className="text-xs font-semibold text-slate-500">범위</span>
                <select value={scope} onChange={(e) => setScope(e.target.value as 'GLOBAL' | 'COMPANY')} className="input mt-1 bg-white">
                  <option value="GLOBAL">전역 (모든 회사)</option>
                  <option value="COMPANY" disabled={!company?.id}>{company?.name ?? '내 회사'}</option>
                </select>
              </label>
            )}
            <label className="block md:col-span-1">
              <span className="text-xs font-semibold text-slate-500">DOCX 파일</span>
              <div className="mt-1 relative">
                <input
                  type="file"
                  accept=".docx"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                  className="absolute inset-0 opacity-0 cursor-pointer"
                  aria-label="DOCX 파일 선택"
                />
                <div className="input flex items-center justify-between gap-2 cursor-pointer">
                  <span className={`truncate text-sm ${file ? 'text-slate-900' : 'text-slate-400'}`}>
                    {file ? file.name : '파일을 선택하세요 (.docx)'}
                  </span>
                  <span className="shrink-0 text-xs font-semibold text-brand-700 px-2 py-1 rounded border border-slate-200 bg-white">
                    찾아보기
                  </span>
                </div>
              </div>
            </label>
            <div className="md:col-span-3 flex justify-end">
              <button type="submit" disabled={saving} className="btn-primary disabled:opacity-50">
                {saving ? '업로드 중...' : '업로드'}
              </button>
            </div>
          </form>
          {error && <p className="mt-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>}
        </section>

        <section className="card p-0 overflow-hidden">
          {loading ? (
            <p className="p-6 text-sm text-slate-400">불러오는 중...</p>
          ) : items.length === 0 ? (
            <p className="p-6 text-sm text-slate-400">템플릿이 없습니다.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">이름</th>
                  <th className="px-4 py-3 font-semibold">범위</th>
                  <th className="px-4 py-3 font-semibold">크기</th>
                  <th className="px-4 py-3 font-semibold">생성</th>
                  <th className="px-4 py-3 font-semibold text-right">작업</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((t) => {
                  const writable = isAdmin || (t.company_id != null && t.company_id === company?.id);
                  return (
                    <tr key={t.id}>
                      <td className="px-4 py-3 font-semibold text-slate-900">{t.name}</td>
                      <td className="px-4 py-3 text-slate-500">{t.company_id == null ? '전역' : `회사 #${t.company_id}`}</td>
                      <td className="px-4 py-3 text-slate-500">{t.file_size != null ? `${Math.round(t.file_size / 1024)} KB` : '-'}</td>
                      <td className="px-4 py-3 text-slate-500">{t.created_at.replace('T', ' ').slice(0, 16)}</td>
                      <td className="px-4 py-3 text-right space-x-2">
                        <button type="button" onClick={() => download(t.id, t.name)} className="text-xs text-slate-600 hover:text-slate-900">다운로드</button>
                        {writable && <button type="button" onClick={() => rename(t.id, t.name)} className="text-xs text-slate-600 hover:text-slate-900">이름변경</button>}
                        {writable && <button type="button" onClick={() => remove(t.id, t.name)} className="text-xs text-rose-600 hover:text-rose-800">삭제</button>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </section>

        <section className="card">
          <h2 className="text-base font-bold text-slate-900 mb-2">지원 placeholder</h2>
          <ul className="text-sm text-slate-700 grid grid-cols-2 gap-x-4 gap-y-1">
            <li><code>{'{title}'}</code> 작업계획서 제목</li>
            <li><code>{'{site_name}'}</code> 현장명</li>
            <li><code>{'{bp_company_name}'}</code> BP 회사명</li>
            <li><code>{'{work_date}'}</code> 작업일</li>
            <li><code>{'{start_time}'}</code> / <code>{'{end_time}'}</code> 시간</li>
            <li><code>{'{work_location}'}</code> 작업 위치</li>
            <li><code>{'{description}'}</code> 상세</li>
            <li><code>{'{status}'}</code> 상태</li>
            <li><code>{'{equipment_list}'}</code> 장비 목록 (줄바꿈)</li>
            <li><code>{'{person_list}'}</code> 인원 목록 (줄바꿈)</li>
            <li><code>{'{equipment_count}'}</code> / <code>{'{person_count}'}</code></li>
            <li><code>{'{printed_at}'}</code> 출력 일시</li>
          </ul>
        </section>
      </div>
    </AppShell>
  );
}
