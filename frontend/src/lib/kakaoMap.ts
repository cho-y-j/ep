/**
 * 카카오 지도 SDK 로더 — 첫 호출 시 script 태그 주입 + autoload=false 로 로드. 이후 호출은 캐시된 promise 반환.
 * `services` (geocoder) + `drawing` (폴리곤 그리기) 라이브러리 포함.
 */

declare global {
  interface Window {
    kakao?: any;
  }
}

let loaderPromise: Promise<typeof window.kakao> | null = null;

export function loadKakaoMap(): Promise<typeof window.kakao> {
  if (loaderPromise) return loaderPromise;

  loaderPromise = new Promise((resolve, reject) => {
    if (typeof window === 'undefined') {
      reject(new Error('window 없음'));
      return;
    }
    if (window.kakao?.maps) {
      window.kakao.maps.load(() => resolve(window.kakao));
      return;
    }
    const appkey = import.meta.env.VITE_KAKAO_JS_KEY as string | undefined;
    if (!appkey) {
      reject(new Error('VITE_KAKAO_JS_KEY 환경변수가 설정되지 않았습니다.'));
      return;
    }
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appkey}&libraries=services,drawing&autoload=false`;
    script.async = true;
    script.onload = () => {
      window.kakao!.maps.load(() => resolve(window.kakao));
    };
    script.onerror = () => reject(new Error('카카오 지도 SDK 로드 실패'));
    document.head.appendChild(script);
  });

  return loaderPromise;
}
