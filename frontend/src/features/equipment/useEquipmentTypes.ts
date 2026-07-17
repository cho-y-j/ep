import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';

/** /api/equipment-types 공개 옵션 (활성 종류, 정렬순). */
export type EquipmentTypeOption = { code: string; name: string; grp: string };

// 작은 공개 목록이라 모듈 단위로 1회만 가져와 여러 드롭다운이 공유.
let cache: EquipmentTypeOption[] | null = null;
let inflight: Promise<EquipmentTypeOption[]> | null = null;

function fetchTypes(): Promise<EquipmentTypeOption[]> {
  if (cache) return Promise.resolve(cache);
  if (!inflight) {
    inflight = api
      .get<EquipmentTypeOption[]>('/api/equipment-types')
      .then((r) => {
        cache = r.data;
        inflight = null;
        return r.data;
      })
      .catch((e) => {
        inflight = null;
        throw e;
      });
  }
  return inflight;
}

/**
 * 활성 장비 종류(어드민 관리)를 런타임 조회 — 드롭다운 소스 + code→name 라벨.
 * enum 소멸 후 새 코드도 여기서 나온다. 미조회/실패 시 정적 EQUIPMENT_CATEGORY_LABEL 폴백.
 */
export function useEquipmentTypes() {
  const [options, setOptions] = useState<EquipmentTypeOption[]>(cache ?? []);

  useEffect(() => {
    let alive = true;
    fetchTypes()
      .then((data) => {
        if (alive) setOptions(data);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const labelOf = (code: string | null | undefined): string => {
    if (!code) return '';
    const dyn = options.find((o) => o.code === code);
    if (dyn) return dyn.name;
    return EQUIPMENT_CATEGORY_LABEL[code as EquipmentCategory] ?? code;
  };

  return { options, labelOf };
}
