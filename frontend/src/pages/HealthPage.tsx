import { useEffect, useState } from 'react';
import { api } from '../lib/api';

type HealthResponse = {
  status: string;
  service: string;
  timestamp: string;
};

export default function HealthPage() {
  const [data, setData] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<HealthResponse>('/api/health')
      .then((res) => setData(res.data))
      .catch((err) => setError(err.message ?? 'failed'));
  }, []);

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="card max-w-md w-full">
        <h1 className="text-2xl font-bold mb-2">SKEP v2</h1>
        <p className="text-slate-500 mb-4">scaffold ready</p>
        {error && <p className="text-red-600">backend: {error}</p>}
        {data && (
          <pre className="text-xs bg-slate-100 p-3 rounded">
            {JSON.stringify(data, null, 2)}
          </pre>
        )}
      </div>
    </main>
  );
}
