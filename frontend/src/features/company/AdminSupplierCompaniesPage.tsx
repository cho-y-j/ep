import AdminCompaniesPage from './AdminCompaniesPage';

export default function AdminSupplierCompaniesPage() {
  return (
    <AdminCompaniesPage
      filterTypes={['EQUIPMENT', 'MANPOWER', 'SAFETY_INSPECTION']}
      title="공급사 관리"
      breadcrumbLabel="공급사 관리"
      description="장비공급사·인력공급사·안전점검회사 통합 목록. 행을 클릭하면 그 회사의 장비/인원/서류를 볼 수 있습니다."
    />
  );
}
