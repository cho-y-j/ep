import { loadKakaoMap } from './kakaoMap';

export type GeocodeResult = {
  lat: number;
  lng: number;
  /** 카카오가 표준화한 도로명/지번 주소. */
  formattedAddress: string;
};

/**
 * 주소 → 좌표 변환. 결과 없으면 null.
 * 카카오 services.Geocoder 사용 — script 라이브러리에 `services` 포함되어 있어야 함.
 */
export async function geocodeAddress(address: string): Promise<GeocodeResult | null> {
  if (!address || !address.trim()) return null;
  const kakao = await loadKakaoMap();
  return new Promise((resolve) => {
    const geocoder = new kakao.maps.services.Geocoder();
    geocoder.addressSearch(address, (result: any[], status: string) => {
      if (status !== kakao.maps.services.Status.OK || !result?.length) {
        resolve(null);
        return;
      }
      const r = result[0];
      resolve({
        lat: parseFloat(r.y),
        lng: parseFloat(r.x),
        formattedAddress: r.road_address?.address_name ?? r.address?.address_name ?? address,
      });
    });
  });
}
