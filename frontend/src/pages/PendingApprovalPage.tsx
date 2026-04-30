import { Link } from 'react-router-dom';

export default function PendingApprovalPage() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="card max-w-md w-full text-center">
        <h1 className="text-2xl font-bold mb-2">승인 대기 중</h1>
        <p className="text-slate-500 mb-6">
          계정이 생성되었습니다. 관리자의 승인을 기다려주세요.
          <br />
          승인 후 로그인할 수 있습니다.
        </p>
        <Link to="/login" className="btn-primary inline-flex">
          로그인 화면으로
        </Link>
      </div>
    </main>
  );
}
