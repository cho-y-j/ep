import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { DocumentResponse, DocumentTypeResponse, OwnerType } from '../../types/document';

type Props = {
  ownerType: OwnerType;
  ownerId: number;
  onUploaded: (doc: DocumentResponse) => void;
  onCancel: () => void;
};

export default function DocumentUploadForm({ ownerType, ownerId, onUploaded, onCancel }: Props) {
  const [types, setTypes] = useState<DocumentTypeResponse[]>([]);
  const [typeId, setTypeId] = useState<number | ''>('');
  const [expiryDate, setExpiryDate] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: ownerType } })
      .then((res) => setTypes(res.data));
  }, [ownerType]);

  const selectedType = useMemo(
    () => types.find((t) => t.id === typeId),
    [types, typeId]
  );

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!typeId) {
      setError('서류 종류를 선택하세요');
      return;
    }
    if (!file) {
      setError('파일을 선택하세요');
      return;
    }
    if (selectedType?.has_expiry && !expiryDate) {
      setError(`${selectedType.name}은(는) 만료일 입력이 필요합니다`);
      return;
    }

    setBusy(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const params: Record<string, string> = {
        ownerType,
        ownerId: String(ownerId),
        documentTypeId: String(typeId),
      };
      if (expiryDate) params.expiryDate = expiryDate;
      const res = await api.post<DocumentResponse>('/api/documents', formData, { params });
      onUploaded(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '업로드 실패');
      } else {
        setError('업로드 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-3 p-4 rounded-lg border border-slate-200 bg-slate-50">
      <h4 className="text-sm font-bold text-slate-700">서류 추가</h4>

      <label className="block">
        <span className="text-xs font-medium text-slate-600">서류 종류</span>
        <select
          value={typeId}
          onChange={(e) => setTypeId(e.target.value === '' ? '' : Number(e.target.value))}
          required
          className="input bg-white mt-1"
        >
          <option value="">— 선택 —</option>
          {types.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
              {t.has_expiry ? ' (만료일 필수)' : ''}
              {t.requires_verification ? ' · 검증 필요' : ''}
            </option>
          ))}
        </select>
      </label>

      {selectedType?.has_expiry && (
        <label className="block">
          <span className="text-xs font-medium text-slate-600">만료일</span>
          <input
            type="date"
            value={expiryDate}
            onChange={(e) => setExpiryDate(e.target.value)}
            required
            className="input mt-1"
          />
        </label>
      )}

      <label className="block">
        <span className="text-xs font-medium text-slate-600">파일</span>
        <input
          type="file"
          accept="image/*,application/pdf"
          capture="environment"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          required
          className="block w-full mt-1 text-sm text-slate-700 file:mr-4 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:bg-brand-600 file:text-white file:cursor-pointer hover:file:bg-brand-700"
        />
      </label>

      {error && (
        <p className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1">{error}</p>
      )}

      <div className="flex justify-end gap-2 pt-1">
        <button type="button" onClick={onCancel} className="text-sm px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100">
          취소
        </button>
        <button type="submit" disabled={busy} className="text-sm px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
          {busy ? '업로드 중...' : '업로드'}
        </button>
      </div>
    </form>
  );
}
