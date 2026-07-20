import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { listConsultations, handleConsultation, type Consultation } from '../../lib/consultation';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, DataTable, EmptyState } from '../../components/ui';
import type { Column } from '../../components/ui';

function fmtDateTime(iso: string) {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export default function ConsultationsPage() {
  const [rows, setRows] = useState<Consultation[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      setRows(await listConsultations());
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function markHandled(c: Consultation) {
    try {
      await handleConsultation(c.id);
      setRows((prev) => prev.map((x) => (x.id === c.id ? { ...x, handled: true } : x)));
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '처리 실패');
    }
  }

  const columns: Column<Consultation>[] = [
    {
      key: 'created', header: '접수일', width: '150px',
      cell: (c) => <span className="text-xs text-slate-500">{fmtDateTime(c.created_at)}</span>,
    },
    { key: 'company', header: '업체명', cell: (c) => c.company_name },
    { key: 'contact', header: '담당자', cell: (c) => c.contact_name },
    { key: 'phone', header: '연락처', cell: (c) => c.phone },
    { key: 'email', header: '이메일', cell: (c) => c.email ?? <span className="text-slate-400">—</span> },
    { key: 'message', header: '문의내용', cell: (c) => <span className="whitespace-pre-wrap text-slate-600">{c.message}</span> },
    {
      key: 'status', header: '상태', width: '110px',
      cell: (c) => c.handled
        ? <span className="inline-flex rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">처리완료</span>
        : <span className="inline-flex rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">대기</span>,
    },
    {
      key: 'actions', header: '', width: '110px',
      cell: (c) => !c.handled && (
        <button
          type="button"
          onClick={() => void markHandled(c)}
          className="rounded-lg bg-brand-600 px-2 py-1 text-xs text-white hover:bg-brand-700"
        >
          처리완료
        </button>
      ),
    },
  ];

  return (
    <AppShell breadcrumb={[{ label: '상담 요청' }]}>
      <div className="mx-auto max-w-6xl px-6 py-8">
        <PageHeader title="상담 요청" subtitle="공개 랜딩페이지에서 접수된 상담 문의입니다." />
        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : rows.length === 0 ? (
          <EmptyState title="접수된 상담이 없습니다" text="공개 랜딩페이지에서 상담이 접수되면 여기에 표시됩니다." />
        ) : (
          <DataTable columns={columns} rows={rows} rowKey={(c) => c.id} />
        )}
      </div>
    </AppShell>
  );
}
