import { useEffect, useRef, useState } from 'react';
import KakaoMap, { type KakaoMapHandle, type LatLng, type PolygonGeoJson } from '../../components/KakaoMap';
import { geocodeAddress } from '../../lib/kakaoGeocoder';

type Props = {
  /** 주소 (외부 상태로 관리, 변경 시 자동 geocode). */
  address: string;
  /** 부모에 좌표/폴리곤/줌 변경 알림 — 폼에 저장 직전 state. */
  onChange: (val: { latitude: number | null; longitude: number | null; polygonGeojson: string | null; mapZoom: number | null }) => void;
  /** 편집 기존 데이터 prefill (수정 모드). */
  initial?: {
    latitude?: number | null;
    longitude?: number | null;
    polygonGeojson?: string | null;
    mapZoom?: number | null;
  };
  height?: string;
};

export default function SiteMapEditor({ address, onChange, initial, height = '420px' }: Props) {
  const mapRef = useRef<KakaoMapHandle>(null);
  const [center, setCenter] = useState<LatLng | null>(
    initial?.latitude && initial?.longitude ? { lat: initial.latitude, lng: initial.longitude } : null
  );
  const [polygon, setPolygon] = useState<PolygonGeoJson | null>(() => {
    if (!initial?.polygonGeojson) return null;
    try { return JSON.parse(initial.polygonGeojson) as PolygonGeoJson; } catch { return null; }
  });
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lastSearchedAddress = useRef<string>('');
  const onChangeRef = useRef(onChange);
  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);

  // 주소 변경 시 자동 geocode (debounce 600ms)
  useEffect(() => {
    if (!address || !address.trim()) return;
    if (address === lastSearchedAddress.current) return;
    const t = setTimeout(async () => {
      setSearching(true);
      setError(null);
      try {
        const result = await geocodeAddress(address);
        lastSearchedAddress.current = address;
        if (result) {
          const next = { lat: result.lat, lng: result.lng };
          setCenter(next);
          mapRef.current?.panTo(next, initial?.mapZoom ?? 4);
          onChangeRef.current({
            latitude: result.lat,
            longitude: result.lng,
            polygonGeojson: polygon ? JSON.stringify(polygon) : null,
            mapZoom: mapRef.current?.getZoom() ?? null,
          });
        } else {
          setError('주소로 좌표를 찾지 못했습니다. 지도에서 직접 클릭해 위치를 지정하세요.');
        }
      } finally {
        setSearching(false);
      }
    }, 600);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [address]);

  const handleMapClick = (pos: LatLng) => {
    setCenter(pos);
    onChangeRef.current({
      latitude: pos.lat,
      longitude: pos.lng,
      polygonGeojson: polygon ? JSON.stringify(polygon) : null,
      mapZoom: mapRef.current?.getZoom() ?? null,
    });
  };

  const handlePolygonComplete = (geo: PolygonGeoJson) => {
    setPolygon(geo);
    onChangeRef.current({
      latitude: center?.lat ?? null,
      longitude: center?.lng ?? null,
      polygonGeojson: JSON.stringify(geo),
      mapZoom: mapRef.current?.getZoom() ?? null,
    });
  };

  const startDrawPolygon = () => {
    setPolygon(null);
    mapRef.current?.clearPolygon();
    mapRef.current?.startDrawPolygon();
  };
  const startDrawRectangle = () => {
    setPolygon(null);
    mapRef.current?.clearPolygon();
    mapRef.current?.startDrawRectangle();
  };
  const startDrawCircle = () => {
    setPolygon(null);
    mapRef.current?.clearPolygon();
    mapRef.current?.startDrawCircle();
  };

  const clearPolygon = () => {
    setPolygon(null);
    mapRef.current?.clearPolygon();
    onChangeRef.current({
      latitude: center?.lat ?? null,
      longitude: center?.lng ?? null,
      polygonGeojson: null,
      mapZoom: mapRef.current?.getZoom() ?? null,
    });
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-xs">
        <div className="flex items-center gap-2">
          <span className="text-slate-500">위치</span>
          {center ? (
            <span className="font-mono text-slate-700">{center.lat.toFixed(5)}, {center.lng.toFixed(5)}</span>
          ) : (
            <span className="text-slate-400">{searching ? '주소 검색 중…' : '주소 입력 또는 지도 클릭'}</span>
          )}
        </div>
        <div className="flex items-center gap-1.5">
          <button type="button" onClick={startDrawPolygon} className="px-2.5 py-1.5 rounded-md border border-blue-300 text-blue-700 hover:bg-blue-50 text-xs">
            다각형
          </button>
          <button type="button" onClick={startDrawRectangle} className="px-2.5 py-1.5 rounded-md border border-blue-300 text-blue-700 hover:bg-blue-50 text-xs">
            사각형
          </button>
          <button type="button" onClick={startDrawCircle} className="px-2.5 py-1.5 rounded-md border border-blue-300 text-blue-700 hover:bg-blue-50 text-xs">
            원
          </button>
          {polygon && (
            <button type="button" onClick={clearPolygon} className="px-2.5 py-1.5 rounded-md border border-slate-300 text-slate-600 hover:bg-slate-50 text-xs">
              삭제
            </button>
          )}
        </div>
      </div>
      {error && (
        <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded px-2 py-1.5">{error}</div>
      )}
      <KakaoMap
        ref={mapRef}
        center={center}
        zoom={initial?.mapZoom ?? 4}
        markers={center ? [{ id: 'site', position: center, color: 'blue', label: '현장' }] : []}
        polygon={polygon}
        onMapClick={handleMapClick}
        onPolygonComplete={handlePolygonComplete}
        height={height}
      />
      <p className="text-[11px] text-slate-400">
        주소 입력 시 좌표가 자동으로 찾아집니다. 지도를 직접 클릭해 위치를 옮길 수 있습니다.
        작업 범위는 <strong>다각형</strong>(클릭으로 점 추가, 더블클릭으로 완료), <strong>사각형/원</strong>(드래그)으로 그립니다.
      </p>
    </div>
  );
}
