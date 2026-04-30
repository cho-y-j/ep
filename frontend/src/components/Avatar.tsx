import { useEffect, useState } from 'react';
import { api } from '../lib/api';

type Props = {
  src?: string;          // 직접 URL이 있으면 사용 (드물게)
  fetchUrl?: string;     // 인증 필요한 API URL (예: /api/persons/3/photo) — blob 가져옴
  alt?: string;
  fallbackText?: string; // 사진 없을 때 보일 글자 (이름 첫글자 등)
  size?: number;         // px
  className?: string;
  rounded?: 'full' | 'lg';
};

export default function Avatar({ src, fetchUrl, alt, fallbackText, size = 40, className, rounded = 'full' }: Props) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [errored, setErrored] = useState(false);

  useEffect(() => {
    setErrored(false);
    if (src) {
      setBlobUrl(src);
      return;
    }
    if (!fetchUrl) {
      setBlobUrl(null);
      return;
    }
    let revoked: string | null = null;
    api.get(fetchUrl, { responseType: 'blob' })
      .then((res) => {
        const url = URL.createObjectURL(res.data);
        revoked = url;
        setBlobUrl(url);
      })
      .catch(() => setErrored(true));
    return () => {
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [src, fetchUrl]);

  const radiusClass = rounded === 'full' ? 'rounded-full' : 'rounded-lg';
  const baseClass = `inline-flex items-center justify-center bg-slate-200 text-slate-500 font-medium overflow-hidden shrink-0 ${radiusClass} ${className ?? ''}`;
  const style = { width: size, height: size, fontSize: size * 0.4 };

  if (blobUrl && !errored) {
    return (
      <span className={baseClass} style={style}>
        <img src={blobUrl} alt={alt ?? ''} className="w-full h-full object-cover" />
      </span>
    );
  }
  return (
    <span className={baseClass} style={style} aria-label={alt}>
      {fallbackText ? fallbackText.charAt(0) : '?'}
    </span>
  );
}
