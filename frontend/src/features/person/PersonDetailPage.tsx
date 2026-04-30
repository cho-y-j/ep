import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import Avatar from '../../components/Avatar';
import ConfirmDialog from '../../components/ConfirmDialog';
import PersonFields, { type PersonFieldValues } from './PersonFields';
import DocumentSection from '../document/DocumentSection';
import { PERSON_ROLE_LABEL, type PersonResponse } from '../../types/person';
import type { CompanyResponse, CompanyType } from '../../types/auth';

function toFieldValues(p: PersonResponse): PersonFieldValues {
  return {
    supplierId: p.supplier_id,
    name: p.name,
    birth: p.birth ?? '',
    phone: p.phone ?? '',
    roles: [...p.roles],
  };
}

export default function PersonDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [person, setPerson] = useState<PersonResponse | null>(null);
  const [supplier, setSupplier] = useState<CompanyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<PersonFieldValues | null>(null);
  const [saveBusy, setSaveBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoNonce, setPhotoNonce] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isAdmin = user?.role === 'ADMIN';
  const canEdit = useMemo(() => {
    if (!person || !user) return false;
    if (user.role === 'ADMIN') return true;
    if (user.role === 'EQUIPMENT_SUPPLIER' || user.role === 'MANPOWER_SUPPLIER') {
      return person.supplier_id === user.company_id;
    }
    return false;
  }, [person, user]);

  const supplierType: CompanyType | undefined = supplier?.type;

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const personRes = await api.get<PersonResponse>(`/api/persons/${id}`);
      setPerson(personRes.data);
      setValues(toFieldValues(personRes.data));
      // supplier 정보 (ADMIN은 전체 조회, 그 외는 본인 회사만 알면 됨)
      if (isAdmin) {
        try {
          const compRes = await api.get<CompanyResponse>(`/api/companies/${personRes.data.supplier_id}`);
          setSupplier(compRes.data);
        } catch {
          setSupplier(null);
        }
      } else {
        // 자기 회사만 보면 됨 — useAuth에서 company 가져오기
      }
    } catch (err) {
      if (err instanceof AxiosError) {
        setLoadError(err.response?.data?.message ?? '인원 정보 불러오기 실패');
      } else {
        setLoadError('인원 정보 불러오기 실패');
      }
    } finally {
      setLoading(false);
    }
  }, [id, isAdmin]);

  useEffect(() => { void load(); }, [load]);

  function startEdit() {
    if (!person) return;
    setValues(toFieldValues(person));
    setSaveError(null);
    setEditing(true);
  }

  async function save() {
    if (!person || !values) return;
    if (values.roles.length === 0) {
      setSaveError('역할을 1개 이상 선택하세요');
      return;
    }
    setSaveBusy(true);
    setSaveError(null);
    try {
      const res = await api.patch<PersonResponse>(`/api/persons/${person.id}`, {
        name: values.name,
        birth: values.birth || null,
        phone: values.phone || null,
        roles: values.roles,
      });
      setPerson(res.data);
      setValues(toFieldValues(res.data));
      setEditing(false);
    } catch (err) {
      if (err instanceof AxiosError) {
        setSaveError(err.response?.data?.message ?? '저장 실패');
      } else {
        setSaveError('저장 실패');
      }
    } finally {
      setSaveBusy(false);
    }
  }

  async function doDelete() {
    if (!person) return;
    setDeleteBusy(true);
    try {
      await api.delete(`/api/persons/${person.id}`);
      navigate('/persons', { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '삭제 실패');
      }
    } finally {
      setDeleteBusy(false);
    }
  }

  async function handlePhotoFile(file: File) {
    if (!person) return;
    setPhotoBusy(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await api.post<PersonResponse>(`/api/persons/${person.id}/photo`, formData);
      setPerson(res.data);
      setPhotoNonce((n) => n + 1);
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '사진 업로드 실패');
      }
    } finally {
      setPhotoBusy(false);
    }
  }

  async function deletePhoto() {
    if (!person) return;
    setPhotoBusy(true);
    try {
      const res = await api.delete<PersonResponse>(`/api/persons/${person.id}/photo`);
      setPerson(res.data);
      setPhotoNonce((n) => n + 1);
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '사진 삭제 실패');
      }
    } finally {
      setPhotoBusy(false);
    }
  }

  if (loading) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="max-w-6xl mx-auto px-6 py-8 text-slate-400">불러오는 중...</div>
      </main>
    );
  }

  if (loadError || !person) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="max-w-6xl mx-auto px-6 py-8">
          <div className="card text-center py-12">
            <p className="text-slate-700 mb-4">{loadError ?? '인원을 찾을 수 없습니다'}</p>
            <Link to="/persons" className="btn-primary inline-flex">목록으로</Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/persons" className="text-sm text-slate-500 hover:text-slate-900">← 목록</Link>
            <h1 className="text-2xl font-bold">인원 상세</h1>
          </div>
          {canEdit && !editing && (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setConfirmDelete(true)}
                className="px-3 py-1.5 rounded-lg bg-red-600 text-white text-sm font-medium hover:bg-red-700"
              >
                삭제
              </button>
              <button type="button" onClick={startEdit} className="btn-primary text-sm py-1.5">수정</button>
            </div>
          )}
          {editing && (
            <div className="flex gap-2">
              <button type="button" onClick={() => setEditing(false)} className="px-3 py-1.5 rounded-lg text-slate-700 text-sm hover:bg-slate-100">
                취소
              </button>
              <button type="button" onClick={save} disabled={saveBusy} className="btn-primary text-sm py-1.5 disabled:opacity-50">
                {saveBusy ? '저장 중...' : '저장'}
              </button>
            </div>
          )}
        </div>

        {/* 정보 카드 */}
        <section className="card">
          <h2 className="text-base font-bold mb-4">정보</h2>

          {editing && values ? (
            <div className="space-y-4">
              <PersonFields values={values} onChange={setValues} supplierType={supplierType} required />
              {saveError && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{saveError}</p>
              )}
            </div>
          ) : (
            <div className="flex flex-col md:flex-row gap-6">
              <div className="flex flex-col items-center gap-2 shrink-0">
                <Avatar
                  key={photoNonce}
                  fetchUrl={person.has_photo ? `/api/persons/${person.id}/photo` : undefined}
                  fallbackText={person.name}
                  alt={person.name}
                  size={140}
                  rounded="lg"
                />
                {canEdit && (
                  <div className="flex flex-col gap-1 w-full">
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      hidden
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) void handlePhotoFile(f);
                        e.target.value = '';
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      disabled={photoBusy}
                      className="text-xs px-3 py-1.5 rounded-lg bg-slate-100 text-slate-700 hover:bg-slate-200 disabled:opacity-50"
                    >
                      {photoBusy ? '업로드 중...' : person.has_photo ? '사진 변경' : '사진 추가'}
                    </button>
                    {person.has_photo && (
                      <button
                        type="button"
                        onClick={() => void deletePhoto()}
                        disabled={photoBusy}
                        className="text-xs px-3 py-1.5 rounded-lg text-red-600 hover:bg-red-50 disabled:opacity-50"
                      >
                        사진 삭제
                      </button>
                    )}
                  </div>
                )}
              </div>

              <div className="flex-1 min-w-0">
                <div className="mb-4">
                  <h3 className="text-2xl font-bold">{person.name}</h3>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {person.roles.map((r) => (
                      <span key={r} className="inline-flex px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs font-medium">
                        {PERSON_ROLE_LABEL[r]}
                      </span>
                    ))}
                  </div>
                </div>

                <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-3 text-sm">
                  <Field label="생년월일" value={person.birth ?? '—'} />
                  <Field label="휴대폰" value={person.phone ?? '—'} />
                  <Field label="공급사" value={supplier?.name ?? `id=${person.supplier_id}`} />
                  {supplier && <Field label="사업자번호" value={supplier.business_number} />}
                  <Field label="등록일" value={new Date(person.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} />
                </dl>
              </div>
            </div>
          )}
        </section>

        {/* 첨부 서류 카드 */}
        <section className="card">
          <DocumentSection ownerType="PERSON" ownerId={person.id} canEdit={canEdit} />
        </section>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="인원 삭제"
        message={`${person.name} 을(를) 삭제합니다.\n복구할 수 없습니다.`}
        confirmLabel="삭제"
        variant="danger"
        busy={deleteBusy}
        onConfirm={doDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </main>
  );
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-slate-900 mt-0.5">{value}</dd>
    </div>
  );
}
