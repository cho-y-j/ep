import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import DashboardRedirect from '../dashboard/DashboardRedirect';
import ConsultModal from './ConsultModal';

const VALUE_CARDS = [
  {
    icon: '🩺',
    title: '생명을 지키는 안전',
    body: '고령 작업자의 뇌출혈·혈압 이상을 워치가 스스로 학습해 감지 → 관제와 주변 동료에게 즉시 알림 → 골든타임 안에 대응합니다.',
  },
  {
    icon: '🗺️',
    title: '현장을 한눈에',
    body: '흩어진 차량·조종사·안전원·건강 상태를 지도 한 장에 모아 → 지금 누가 어디서 어떤 상태인지 → 관제가 바로 판단합니다.',
  },
  {
    icon: '⚙️',
    title: '서류·투입·정산 자동화',
    body: '서류 링크 수집과 OCR 검증부터 투입, 작업확인서, 거래내역서까지 → 흩어진 수기 업무를 한 흐름으로 → 실수와 반복을 줄입니다.',
  },
  {
    icon: '✅',
    title: '검증된 신뢰',
    body: '제출 서류의 진위를 검증하고 승인제 가입으로 → 검증된 업체만 현장에 들어오도록 → 원청과 BP가 안심하고 맡깁니다.',
  },
  {
    icon: '📈',
    title: '데이터로 진화',
    body: '현장에서 쌓인 안전·투입 데이터가 축적될수록 위험 예측과 최적 투입으로 이어지도록, 원온은 계속 나아갑니다. (로드맵)',
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
  );
}

export default function LandingPage() {
  const { user, loading } = useAuth();
  const [consultOpen, setConsultOpen] = useState(false);

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
            <Link to="/login" className="rounded-md px-3 py-1.5 text-sm font-medium text-white/90 hover:text-white">
              로그인
            </Link>
            <Link to="/signup" className="rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-slate-900 hover:bg-slate-100">
              가입 신청
            </Link>
          </nav>
        </div>
      </header>

      {/* 히어로 */}
      <section className="relative flex min-h-screen items-center justify-center overflow-hidden">
        <BgVideo src="/hero-forest.mp4" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/50 to-black/70" />
        <div className="relative z-10 mx-auto max-w-3xl px-4 text-center text-white">
          <h1 className="text-3xl font-bold leading-tight sm:text-5xl">
            기술은, 조용히 지킬 때<br className="hidden sm:block" /> 가장 강합니다
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-white/85 sm:text-lg">
            원온은 건설현장의 장비·인력 투입관리와 스마트안전을 하나로 잇습니다.
            흩어진 현장을 한 흐름으로, 사람을 먼저 지키는 방식으로.
          </p>
          <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Link
              to="/signup"
              className="w-full rounded-lg bg-brand-600 px-6 py-3 text-center text-sm font-semibold text-white hover:bg-brand-700 sm:w-auto"
            >
              가입 신청
            </Link>
            <button
              type="button"
              onClick={() => setConsultOpen(true)}
              className="w-full rounded-lg bg-white/10 px-6 py-3 text-center text-sm font-semibold text-white ring-1 ring-inset ring-white/40 backdrop-blur hover:bg-white/20 sm:w-auto"
            >
              상담 요청
            </button>
          </div>
        </div>
      </section>

      {/* 핵심 가치 5 */}
      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-2xl font-bold sm:text-3xl">원온이 현장에 주는 것</h2>
          <p className="mt-3 text-slate-500">안전부터 자동화, 신뢰까지 — 현장이 실제로 겪는 문제에서 출발했습니다.</p>
        </div>
        <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {VALUE_CARDS.map((c) => (
            <div key={c.title} className="card">
              <div className="text-3xl">{c.icon}</div>
              <h3 className="mt-4 text-lg font-bold text-slate-900">{c.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{c.body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 무드 섹션 */}
      <section className="relative flex min-h-[60vh] items-center justify-center overflow-hidden">
        <BgVideo src="/hero-macro.mp4" className="absolute inset-0 h-full w-full object-cover" />
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
