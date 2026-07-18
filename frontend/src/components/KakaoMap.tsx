import { useEffect, useImperativeHandle, useRef, useState, forwardRef } from 'react';
import { loadKakaoMap } from '../lib/kakaoMap';

export type LatLng = { lat: number; lng: number };

export type PolygonGeoJson = {
  type: 'Polygon';
  coordinates: number[][][]; // [[[lng, lat], ...]]
};

export type MapCircle = {
  id: string;
  center: LatLng;
  radiusM: number;
  /** 색상 (테두리/채움). 기본 amber. */
  color?: 'emerald' | 'blue' | 'amber' | 'rose' | 'slate';
  /** 라벨 (지도 위 표시는 안 함, 추후 확장). */
  label?: string;
};

export type MapMarker = {
  id: string;
  position: LatLng;
  /** 마커 라벨 텍스트 (말풍선 / 마커 위 표시). */
  label?: string;
  /** 색상 키 — emerald(기본), blue, amber, rose. */
  color?: 'emerald' | 'blue' | 'amber' | 'rose' | 'slate';
  /** 클릭 시 호출. */
  onClick?: () => void;
  /** hover/클릭 시 띄울 InfoWindow HTML. 비어있으면 안 띄움. */
  tooltipHtml?: string;
  /** true 면 마커 뒤에 펄스 링(강조) — 안전 상황판 미확인 경보. 기본 false. */
  pulse?: boolean;
};

export type KakaoMapHandle = {
  /** 좌표로 지도 중심 이동 + 줌 옵션. */
  panTo: (pos: LatLng, zoom?: number) => void;
  /** 다각형 그리기 시작 (클릭으로 점 추가, 더블클릭으로 완료). */
  startDrawPolygon: () => void;
  /** 사각형 그리기 시작 (drag). */
  startDrawRectangle: () => void;
  /** 원 그리기 시작 (drag — 중심에서 바깥으로 당김). */
  startDrawCircle: () => void;
  /** 현재 도형 삭제. */
  clearPolygon: () => void;
  /** 현재 지도 zoom level 가져오기. */
  getZoom: () => number;
};

type Props = {
  /** 초기 중심. 미지정시 서울시청. */
  center?: LatLng | null;
  /** 초기 줌. 1(가까움) ~ 14(전국). 기본 4. */
  zoom?: number;
  /** 표시할 마커들. id 가 같으면 동일 마커로 간주. */
  markers?: MapMarker[];
  /** 원(반경) 그리기 — 현장 지오펜스 등. */
  circles?: MapCircle[];
  /** 폴리곤 GeoJSON. null 이면 그리지 않음. */
  polygon?: PolygonGeoJson | null;
  /** 폴리곤 그리기 완료 시 호출. */
  onPolygonComplete?: (geo: PolygonGeoJson) => void;
  /** 지도 클릭 시 호출 (좌표 입력 모드). */
  onMapClick?: (pos: LatLng) => void;
  /** 높이 (CSS). 기본 '480px'. */
  height?: string;
  /** 추가 className. */
  className?: string;
};

const SEOUL_CITY_HALL: LatLng = { lat: 37.5665, lng: 126.9780 };

const MARKER_COLOR_HEX: Record<NonNullable<MapMarker['color']>, string> = {
  emerald: '#10b981',
  blue: '#3b82f6',
  amber: '#f59e0b',
  rose: '#ef4444',
  slate: '#64748b',
};

function buildMarkerSvg(color: string, label?: string): string {
  const fill = color.replace('#', '%23');
  const txt = (label ?? '').slice(0, 2);
  return `data:image/svg+xml;charset=utf-8,${
    encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="36" height="48" viewBox="0 0 36 48">
      <path d="M18 0C8.06 0 0 8.06 0 18c0 13.5 18 30 18 30s18-16.5 18-30C36 8.06 27.94 0 18 0z" fill="${color}" stroke="white" stroke-width="2"/>
      <circle cx="18" cy="18" r="6" fill="white"/>
      ${txt ? `<text x="18" y="22" font-size="9" font-weight="bold" text-anchor="middle" fill="${color}">${txt}</text>` : ''}
    </svg>`)
  }`.replace(`%23${fill}`, fill); // ensure
}

const KakaoMap = forwardRef<KakaoMapHandle, Props>(function KakaoMap(
  { center, zoom = 4, markers = [], circles = [], polygon = null, onPolygonComplete, onMapClick, height = '480px', className }, ref
) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const markersRef = useRef<Map<string, any>>(new Map());
  const infoWindowsRef = useRef<Map<string, any>>(new Map());
  const pulseOverlaysRef = useRef<Map<string, any>>(new Map());
  const openInfoWindowIdRef = useRef<string | null>(null);
  const circlesRef = useRef<Map<string, any>>(new Map());
  const polygonRef = useRef<any>(null);
  const drawingManagerRef = useRef<any>(null);
  const onPolygonCompleteRef = useRef(onPolygonComplete);
  const onMapClickRef = useRef(onMapClick);
  const [ready, setReady] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => { onPolygonCompleteRef.current = onPolygonComplete; }, [onPolygonComplete]);
  useEffect(() => { onMapClickRef.current = onMapClick; }, [onMapClick]);

  // SDK 로드 + 지도 인스턴스 생성 (1회).
  useEffect(() => {
    let cancelled = false;
    loadKakaoMap().then((kakao) => {
      if (cancelled || !containerRef.current) return;
      const init = center ?? SEOUL_CITY_HALL;
      mapRef.current = new kakao.maps.Map(containerRef.current, {
        center: new kakao.maps.LatLng(init.lat, init.lng),
        level: zoom,
      });

      // 지도 클릭 핸들러
      kakao.maps.event.addListener(mapRef.current, 'click', (e: any) => {
        if (onMapClickRef.current) {
          onMapClickRef.current({ lat: e.latLng.getLat(), lng: e.latLng.getLng() });
        }
      });

      // drawing manager — 폴리곤 + 사각형 + 원. 모두 GeoJSON Polygon 으로 통일 저장.
      const styleOpts = {
        draggable: false,
        removable: false,
        fillColor: '#3b82f6',
        fillOpacity: 0.18,
        strokeColor: '#1d4ed8',
        strokeWeight: 2,
      };
      drawingManagerRef.current = new kakao.maps.drawing.DrawingManager({
        map: mapRef.current,
        drawingMode: [
          kakao.maps.drawing.OverlayType.POLYGON,
          kakao.maps.drawing.OverlayType.RECTANGLE,
          kakao.maps.drawing.OverlayType.CIRCLE,
        ],
        polygonOptions: styleOpts,
        rectangleOptions: styleOpts,
        circleOptions: styleOpts,
      });
      kakao.maps.event.addListener(drawingManagerRef.current, 'drawend', (e: any) => {
        // 새 도형 그려지면 기존 도형 제거 + GeoJSON Polygon 으로 통일 변환
        if (polygonRef.current) {
          polygonRef.current.setMap(null);
          polygonRef.current = null;
        }

        let pathLngLat: number[][] = [];
        const overlayType = e.overlayType;
        if (overlayType === kakao.maps.drawing.OverlayType.POLYGON) {
          // points: [{x:lng, y:lat}, ...]
          pathLngLat = e.data.points.map((p: any) => [p.x, p.y]);
        } else if (overlayType === kakao.maps.drawing.OverlayType.RECTANGLE) {
          // bound: {sw, ne} or points: 4 corners
          const sw = e.data.bound?.sw ?? { x: e.data.points[0].x, y: e.data.points[0].y };
          const ne = e.data.bound?.ne ?? { x: e.data.points[2].x, y: e.data.points[2].y };
          pathLngLat = [
            [sw.x, sw.y], [ne.x, sw.y], [ne.x, ne.y], [sw.x, ne.y],
          ];
        } else if (overlayType === kakao.maps.drawing.OverlayType.CIRCLE) {
          // {center:{x,y}, radius} — 64각형으로 근사
          const c = e.data.center;
          const r = e.data.radius; // meters
          const segments = 64;
          const latPerMeter = 1 / 111000;
          const lngPerMeter = 1 / (111000 * Math.cos(c.y * Math.PI / 180));
          for (let i = 0; i < segments; i++) {
            const angle = (i / segments) * 2 * Math.PI;
            pathLngLat.push([
              c.x + Math.cos(angle) * r * lngPerMeter,
              c.y + Math.sin(angle) * r * latPerMeter,
            ]);
          }
        }
        if (pathLngLat.length === 0) {
          drawingManagerRef.current.cancel();
          return;
        }
        pathLngLat.push(pathLngLat[0]); // close ring
        const geo: PolygonGeoJson = { type: 'Polygon', coordinates: [pathLngLat] };
        const kakaoPath = pathLngLat.map((cc: number[]) => new kakao.maps.LatLng(cc[1], cc[0]));
        const poly = new kakao.maps.Polygon({
          path: kakaoPath,
          strokeWeight: 2,
          strokeColor: '#1d4ed8',
          strokeOpacity: 0.8,
          fillColor: '#3b82f6',
          fillOpacity: 0.18,
        });
        poly.setMap(mapRef.current);
        polygonRef.current = poly;
        drawingManagerRef.current.cancel();
        onPolygonCompleteRef.current?.(geo);
      });

      setReady(true);
    }).catch((err) => {
      if (cancelled) return;
      console.error('[KakaoMap] SDK 로드 실패', err);
      setLoadError(err?.message ?? '지도 로드 실패');
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // center 변경 시 panTo
  useEffect(() => {
    if (!ready || !center || !window.kakao) return;
    mapRef.current.setCenter(new window.kakao.maps.LatLng(center.lat, center.lng));
  }, [ready, center?.lat, center?.lng]);

  // 마커 reconcile
  useEffect(() => {
    if (!ready || !window.kakao) return;
    const kakao = window.kakao;
    const newIds = new Set(markers.map((m) => m.id));
    // 기존 중 없어진 것 제거
    for (const [id, marker] of markersRef.current) {
      if (!newIds.has(id)) {
        marker.setMap(null);
        const iw = infoWindowsRef.current.get(id);
        if (iw) { iw.close(); infoWindowsRef.current.delete(id); }
        markersRef.current.delete(id);
      }
    }
    // 추가/업데이트
    for (const m of markers) {
      const colorHex = MARKER_COLOR_HEX[m.color ?? 'emerald'];
      const imgSrc = buildMarkerSvg(colorHex, m.label);
      let marker = markersRef.current.get(m.id);
      const pos = new kakao.maps.LatLng(m.position.lat, m.position.lng);
      const markerImage = new kakao.maps.MarkerImage(
        imgSrc,
        new kakao.maps.Size(36, 48),
        { offset: new kakao.maps.Point(18, 48) }
      );
      if (!marker) {
        marker = new kakao.maps.Marker({ map: mapRef.current, position: pos, image: markerImage });
        markersRef.current.set(m.id, marker);
        kakao.maps.event.addListener(marker, 'click', () => {
          // 클릭 시 InfoWindow 토글 + onClick.
          const iw = infoWindowsRef.current.get(m.id);
          if (iw) {
            if (openInfoWindowIdRef.current === m.id) {
              iw.close(); openInfoWindowIdRef.current = null;
            } else {
              const prev = openInfoWindowIdRef.current && infoWindowsRef.current.get(openInfoWindowIdRef.current);
              if (prev) prev.close();
              iw.open(mapRef.current, marker!);
              openInfoWindowIdRef.current = m.id;
            }
          }
          m.onClick?.();
        });
        kakao.maps.event.addListener(marker, 'mouseover', () => {
          const iw = infoWindowsRef.current.get(m.id);
          if (iw && openInfoWindowIdRef.current !== m.id) iw.open(mapRef.current, marker!);
        });
        kakao.maps.event.addListener(marker, 'mouseout', () => {
          const iw = infoWindowsRef.current.get(m.id);
          if (iw && openInfoWindowIdRef.current !== m.id) iw.close();
        });
      } else {
        marker.setPosition(pos);
        marker.setImage(markerImage);
      }
      // InfoWindow 갱신 (HTML 변경 시도 setContent 로 반영)
      const existingIw = infoWindowsRef.current.get(m.id);
      if (m.tooltipHtml) {
        if (existingIw) {
          existingIw.setContent(m.tooltipHtml);
        } else {
          const iw = new kakao.maps.InfoWindow({ content: m.tooltipHtml, removable: false });
          infoWindowsRef.current.set(m.id, iw);
        }
      } else if (existingIw) {
        existingIw.close();
        infoWindowsRef.current.delete(m.id);
      }
    }

    // 펄스 링 reconcile — pulse=true 마커 뒤에 CustomOverlay(강조). 없어지면 제거.
    const pulseIds = new Set(markers.filter((m) => m.pulse).map((m) => m.id));
    for (const [id, ov] of pulseOverlaysRef.current) {
      if (!pulseIds.has(id)) { ov.setMap(null); pulseOverlaysRef.current.delete(id); }
    }
    for (const m of markers) {
      if (!m.pulse) continue;
      const pos = new kakao.maps.LatLng(m.position.lat, m.position.lng);
      const existing = pulseOverlaysRef.current.get(m.id);
      if (existing) {
        existing.setPosition(pos);
      } else {
        const el = document.createElement('div');
        el.className = 'skep-map-pulse';
        const ov = new kakao.maps.CustomOverlay({ position: pos, content: el, zIndex: 1 });
        ov.setMap(mapRef.current);
        pulseOverlaysRef.current.set(m.id, ov);
      }
    }
  }, [ready, markers]);

  // 원(circles) reconcile — 현장 지오펜스 범위.
  useEffect(() => {
    if (!ready || !window.kakao) return;
    const kakao = window.kakao;
    const newIds = new Set(circles.map((c) => c.id));
    for (const [id, circle] of circlesRef.current) {
      if (!newIds.has(id)) {
        circle.setMap(null);
        circlesRef.current.delete(id);
      }
    }
    for (const c of circles) {
      const hex = MARKER_COLOR_HEX[c.color ?? 'amber'];
      const existing = circlesRef.current.get(c.id);
      const center = new kakao.maps.LatLng(c.center.lat, c.center.lng);
      if (existing) {
        existing.setMap(null);
        circlesRef.current.delete(c.id);
      }
      const circle = new kakao.maps.Circle({
        center,
        radius: c.radiusM,
        strokeWeight: 2,
        strokeColor: hex,
        strokeOpacity: 0.8,
        strokeStyle: 'dashed',
        fillColor: hex,
        fillOpacity: 0.12,
      });
      circle.setMap(mapRef.current);
      circlesRef.current.set(c.id, circle);
    }
  }, [ready, circles]);

  // 폴리곤 외부 prop 변경 시 다시 그림
  useEffect(() => {
    if (!ready || !window.kakao) return;
    if (polygonRef.current) {
      polygonRef.current.setMap(null);
      polygonRef.current = null;
    }
    if (!polygon || !polygon.coordinates?.[0]?.length) return;
    const kakao = window.kakao;
    const path = polygon.coordinates[0].map((c) => new kakao.maps.LatLng(c[1], c[0]));
    const poly = new kakao.maps.Polygon({
      path,
      strokeWeight: 2,
      strokeColor: '#1d4ed8',
      strokeOpacity: 0.8,
      fillColor: '#3b82f6',
      fillOpacity: 0.18,
    });
    poly.setMap(mapRef.current);
    polygonRef.current = poly;
  }, [ready, polygon]);

  useImperativeHandle(ref, () => ({
    panTo: (pos, z) => {
      if (!mapRef.current || !window.kakao) return;
      mapRef.current.setCenter(new window.kakao.maps.LatLng(pos.lat, pos.lng));
      if (z != null) mapRef.current.setLevel(z);
    },
    startDrawPolygon: () => {
      if (!drawingManagerRef.current || !window.kakao) return;
      drawingManagerRef.current.select(window.kakao.maps.drawing.OverlayType.POLYGON);
    },
    startDrawRectangle: () => {
      if (!drawingManagerRef.current || !window.kakao) return;
      drawingManagerRef.current.select(window.kakao.maps.drawing.OverlayType.RECTANGLE);
    },
    startDrawCircle: () => {
      if (!drawingManagerRef.current || !window.kakao) return;
      drawingManagerRef.current.select(window.kakao.maps.drawing.OverlayType.CIRCLE);
    },
    clearPolygon: () => {
      if (polygonRef.current) {
        polygonRef.current.setMap(null);
        polygonRef.current = null;
      }
    },
    getZoom: () => mapRef.current?.getLevel() ?? zoom,
  }), [ready, zoom]);

  if (loadError) {
    return (
      <div className={className} style={{ width: '100%', height }}
           role="alert"
           aria-label="지도 로드 실패">
        <div className="h-full w-full flex flex-col items-center justify-center gap-2 rounded-lg border border-amber-200 bg-amber-50 text-center p-6">
          <div className="text-sm font-semibold text-amber-800">지도를 불러올 수 없습니다</div>
          <div className="text-xs text-amber-700 max-w-md">{loadError}</div>
          <div className="text-xs text-slate-500 mt-2">
            카카오 디벨로퍼 콘솔에서 <code>skep.on1.kr</code> 도메인이 등록되어 있는지 확인하세요.
            지도 없이도 주소 입력 + 저장은 정상 동작합니다.
          </div>
        </div>
      </div>
    );
  }
  return <div ref={containerRef} className={className} style={{ width: '100%', height }} />;
});

export default KakaoMap;
