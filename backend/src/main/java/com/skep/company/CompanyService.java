package com.skep.company;

import com.skep.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository repo;

    public CompanyService(CompanyRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Company> listAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public List<Company> listByType(CompanyType type) {
        return repo.findByType(type);
    }

    @Transactional(readOnly = true)
    public Company get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("COMPANY_NOT_FOUND", "company " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Company> findByBusinessNumber(String businessNumber) {
        return repo.findByBusinessNumber(businessNumber);
    }

    public Company create(String name, String businessNumber, CompanyType type) {
        if (repo.existsByBusinessNumber(businessNumber)) {
            throw ApiException.conflict("BUSINESS_NUMBER_EXISTS", "이미 등록된 사업자번호입니다");
        }
        return repo.save(Company.builder()
                .name(name)
                .businessNumber(businessNumber)
                .type(type)
                .build());
    }

    public Company rename(Long id, String newName) {
        Company c = get(id);
        c.rename(newName);
        return c;
    }

    /**
     * Used during signup. Returns existing company if business number matches AND type matches.
     * Throws on type mismatch. Creates new if not found.
     */
    public CompanyResolution resolveOrCreate(String name, String businessNumber, CompanyType type) {
        Optional<Company> existing = repo.findByBusinessNumber(businessNumber);
        if (existing.isPresent()) {
            Company c = existing.get();
            if (c.getType() != type) {
                throw ApiException.conflict("COMPANY_TYPE_MISMATCH",
                        "이미 등록된 사업자번호이지만 회사 유형이 다릅니다");
            }
            return new CompanyResolution(c, false);
        }
        Company created = repo.save(Company.builder()
                .name(name)
                .businessNumber(businessNumber)
                .type(type)
                .build());
        return new CompanyResolution(created, true);
    }

    public record CompanyResolution(Company company, boolean isNew) {}
}
