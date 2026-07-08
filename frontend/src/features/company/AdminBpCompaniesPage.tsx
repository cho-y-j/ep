import AdminCompaniesPage from './AdminCompaniesPage';

export default function AdminBpCompaniesPage() {
  return (
    <AdminCompaniesPage
      filterTypes={['BP']}
      title="BP사 관리"
      breadcrumbLabel="BP사 관리"
      description="시공사·협력사(BP) 목록. 행을 클릭하면 그 회사의 인원/현장/작업계획서를 볼 수 있습니다."
    />
  );
}
