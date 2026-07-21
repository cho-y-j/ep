import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import DashboardRedirect from '../dashboard/DashboardRedirect';
import ConsultModal from './ConsultModal';

type CardIconProps = { className?: string };
const svgBase = {
  viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
  strokeWidth: 1.6, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};
const IconHeartPulse = ({ className }: CardIconProps) => (
  <svg {...svgBase} className={className}>
    <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.29 1.49 4.04 3 5.5l7 7Z" />
    <path d="M3.22 12H9.5l.5-1 2 4.5 2-7 1.5 3.5h4.78" />
  </svg>
);
const IconMapPin = ({ className }: CardIconProps) => (
  <svg {...svgBase} className={className}>
    <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0Z" />
    <circle cx="12" cy="10" r="3" />
  </svg>
);
const IconShieldCheck = ({ className }: CardIconProps) => (
  <svg {...svgBase} className={className}>
    <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1Z" />
    <path d="m9 12 2 2 4-4" />
  </svg>
);

const HERO_SLOGANS = [
  ['사람과 장비,', '하나의 흐름으로'],
  ['안전은 확실하게,', '관리는 가볍게'],
];

const VALUE_CARDS = [
  {
    icon: IconHeartPulse,
    title: '생명을 지키는 스마트 안전',
    body: '워치가 고령 작업자의 뇌출혈·혈압 이상을 스스로 학습해 감지하고, 관제와 주변 동료에게 즉시 알립니다. 골든타임 안에 대응해 중대재해를 막습니다.',
  },
  {
    icon: IconMapPin,
    title: '현장을 한눈에, 자동으로',
    body: '차량·조종사·안전원·건강 상태를 지도 한 장에 모으고, 서류 수집·투입·정산·작업확인서까지 하나의 흐름으로 자동화합니다.',
  },
  {
    icon: IconShieldCheck,
    title: '검증으로 쌓고, 데이터로 진화',
    body: '서류 진위검증과 승인제 가입으로 검증된 업체만 현장에 들어옵니다. 쌓인 안전·투입 데이터는 위험 예측과 최적 투입으로 이어집니다.',
  },
];

const BENEFITS = [
  { title: '안전', body: '이상 징후를 놓치지 않고 빠르게 대응해, 사고를 예방하는 문화를 현장에 정착시킵니다.' },
  { title: '자동화', body: '서류·투입·정산의 반복 업무를 하나의 흐름으로 이어, 담당자의 손이 덜 가게 합니다.' },
  { title: '신뢰', body: '검증된 업체와 투명한 기록으로, 원청·BP·공급사가 같은 화면을 보고 협업합니다.' },
];

/** 배경 영상 — muted 를 ref 로 강제해 모바일 포함 자동재생 보장. */
function BgVideo({ src, className }: { src: string; className?: string }) {
  return (
    <>
      <video
        ref={(el) => { if (el) el.muted = true; }}
        src={src}
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        className={className}
      />
      {/* 생성 영상 우측 하단 워터마크 — 코너 비네트+블러로 은은하게 가림 */}
      <div
        className="pointer-events-none absolute bottom-0 right-0 h-56 w-56 backdrop-blur-[2px]"
        style={{ background: 'radial-gradient(ellipse at bottom right, rgba(0,0,0,0.6), rgba(0,0,0,0.22) 45%, transparent 72%)' }}
      />
    </>
  );
}

export default function LandingPage() {
  const { user, loading } = useAuth();
  const [consultOpen, setConsultOpen] = useState(false);
  const [slogan, setSlogan] = useState(0);

  useEffect(() => {
    const t = setInterval(() => setSlogan((s) => (s + 1) % HERO_SLOGANS.length), 4200);
    return () => clearInterval(t);
  }, []);

  // 인증 로딩 중엔 아무것도 안 그려 로그인 사용자에게 랜딩이 깜빡이지 않게 한다.
  if (loading) return null;
  if (user) return <DashboardRedirect />;

  return (
    <div className="min-h-screen bg-white text-slate-900">
      {/* 상단바 — 히어로 위에 겹침 */}
      <header className="absolute inset-x-0 top-0 z-20">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
          <span className="text-lg font-bold text-white drop-shadow">
            원온 <span className="text-brand-500">ONEON</span>
          </span>
          <nav className="flex items-center gap-2">
            <Link to="/login" className="rounded-md px-4 py-1.5 text-sm font-medium text-white/90 ring-1 ring-inset ring-white/30 transition-colors hover:bg-white/10 hover:text-white">
              로그인
            </Link>
          </nav>
        </div>
      </header>

      {/* 히어로 — 배경 영상 위로 슬로건이 순차 페이드인 (버튼 없음) */}
      <section className="relative flex min-h-screen items-center justify-center overflow-hidden">
        <BgVideo src="/hero-macro.mp4" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/45 to-black/75" />
        <div className="relative z-10 mx-auto max-w-4xl px-6 text-center text-white">
          <p className="hero-rise text-xs font-medium tracking-[0.4em] text-white/70 sm:text-sm" style={{ animationDelay: '0.15s' }}>
            ONEON
          </p>
          <h1 key={slogan} className="slogan-fade mt-7 text-4xl font-normal leading-[1.3] sm:text-6xl">
            <span className="block">{HERO_SLOGANS[slogan][0]}</span>
            <span className="mt-2 block font-semibold">{HERO_SLOGANS[slogan][1]}</span>
          </h1>
          <p className="hero-rise mx-auto mt-9 max-w-xl text-base font-light leading-relaxed text-white/80 sm:text-lg" style={{ animationDelay: '1.0s' }}>
            장비·인력 투입관리와 스마트안전을 하나로 잇는 현장 플랫폼.<br className="hidden sm:block" />
            흩어진 현장을 한 흐름으로, 사람을 먼저 지키는 방식으로.
          </p>
        </div>
        <div className="hero-rise absolute bottom-8 left-1/2 -translate-x-1/2 text-white/60" style={{ animationDelay: '2.7s' }}>
          <div className="hero-scroll-hint flex flex-col items-center gap-1">
            <span className="text-[10px] tracking-[0.3em]">SCROLL</span>
            <span className="text-lg leading-none">↓</span>
          </div>
        </div>
      </section>

      {/* 핵심 가치 5 */}
      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-2xl font-bold sm:text-3xl">원온이 만드는 변화</h2>
          <p className="mt-3 text-slate-500">안전부터 자동화, 신뢰까지 — 현장이 실제로 겪는 문제에서 출발했습니다.</p>
        </div>
        <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {VALUE_CARDS.map((c, i) => {
            const Icon = c.icon;
            return (
              <div
                key={c.title}
                className="card group flex cursor-default flex-col p-8 transition-all duration-300 hover:-translate-y-1.5 hover:border-brand-300 hover:shadow-xl"
              >
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 transition-colors duration-300 group-hover:bg-brand-600 group-hover:text-white">
                  <Icon className="h-7 w-7" />
                </div>
                <span className="mt-6 text-xs font-bold tracking-[0.2em] text-brand-500">0{i + 1}</span>
                <h3 className="mt-2 text-xl font-bold text-slate-900 transition-colors group-hover:text-brand-600">{c.title}</h3>
                <p className="mt-3 flex-1 text-sm leading-relaxed text-slate-600">{c.body}</p>
              </div>
            );
          })}
        </div>
      </section>

      {/* 무드 섹션 */}
      <section className="relative flex min-h-[60vh] items-center justify-center overflow-hidden">
        <BgVideo src="/hero-forest.mp4" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-black/45" />
        <div className="relative z-10 px-4 text-center text-white">
          <p className="text-2xl font-semibold leading-snug sm:text-4xl">
            자연을 닮은 지능,<br /> 사람을 위한 안전
          </p>
        </div>
      </section>

      {/* 도입 효과 */}
      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-2xl font-bold sm:text-3xl">원온을 도입하면</h2>
        </div>
        <div className="mt-12 grid gap-6 sm:grid-cols-3">
          {BENEFITS.map((b) => (
            <div key={b.title} className="text-center sm:text-left">
              <h3 className="text-lg font-bold text-brand-600">{b.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{b.body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 하단 CTA */}
      <section className="bg-slate-900 px-4 py-16 text-center text-white">
        <h2 className="text-2xl font-bold sm:text-3xl">현장을, 사람을 먼저 지키는 방식으로</h2>
        <p className="mx-auto mt-4 max-w-xl text-white/75">
          가입 신청은 승인 후 이용할 수 있습니다. 도입이 궁금하다면 먼저 상담을 요청해 주세요.
        </p>
        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Link
            to="/signup"
            className="w-full rounded-lg bg-brand-600 px-6 py-3 text-sm font-semibold text-white hover:bg-brand-700 sm:w-auto"
          >
            가입 신청
          </Link>
          <button
            type="button"
            onClick={() => setConsultOpen(true)}
            className="w-full rounded-lg bg-white px-6 py-3 text-sm font-semibold text-slate-900 hover:bg-slate-100 sm:w-auto"
          >
            상담 요청
          </button>
        </div>
      </section>

      {/* 푸터 */}
      <footer className="border-t border-slate-200 bg-white px-4 py-10 text-sm text-slate-500">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <span className="font-bold text-slate-900">
            원온 <span className="text-brand-600">ONEON</span>
          </span>
          <div className="text-xs leading-relaxed">
            <p>(주)원온 · 사업자등록번호 000-00-00000</p>
            <p>문의 000-0000-0000 · 서울특별시 ○○구 ○○로 00</p>
          </div>
        </div>
        <p className="mx-auto mt-6 max-w-6xl text-xs text-slate-400">© 2026 ONEON. All rights reserved.</p>
      </footer>

      {consultOpen && <ConsultModal onClose={() => setConsultOpen(false)} />}
    </div>
  );
}
