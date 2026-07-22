import { useEffect, useState } from 'react';
import { api } from '../../lib/api';

/**
 * 내 회사가 취급(선택)한 장비종류 코드 목록.
 * null = 로딩중, [] = 미설정(전체 표시 — 기존 동작), [..] = 이 종류만 기본 표시.
 * 장비 등록·서류 수집요청의 종류 선택을 좁히는 데 공용으로 쓴다.
 */
export function useHandledEquipmentTypes(): string[] | null {
  const [codes, setCodes] = useState<string[] | null>(null);
  useEffect(() => {
    let alive = true;
    api.get<string[]>('/api/companies/me/equipment-types')
      .then((r) => { if (alive) setCodes(r.data ?? []); })
      .catch(() => { if (alive) setCodes([]); });
    return () => { alive = false; };
  }, []);
  return codes;
}

/**
 * 취급종류로 좁힐 판정 함수. 반환값 null = 제한 없음(전체 표시).
 * '전체 보기(showAll)'거나 로딩중/미설정이면 null → 종류 select 는 전체를 그대로 보여준다.
 */
export function handledCodeFilter(
  handled: string[] | null,
  showAll: boolean,
): ((code: string) => boolean) | null {
  if (showAll || !handled || handled.length === 0) return null;
  const set = new Set(handled);
  return (code) => set.has(code);
}
