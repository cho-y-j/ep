CREATE TABLE sites (
    id BIGSERIAL PRIMARY KEY,
    bp_company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(64),
    address VARCHAR(255),
    detail_address VARCHAR(255),
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_sites_code ON sites(code) WHERE code IS NOT NULL;
CREATE INDEX idx_sites_bp_company ON sites(bp_company_id);
CREATE INDEX idx_sites_status ON sites(status);

CREATE TABLE site_participants (
    id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    participant_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    added_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (site_id, company_id)
);

CREATE INDEX idx_site_participants_site ON site_participants(site_id);
CREATE INDEX idx_site_participants_company ON site_participants(company_id);
CREATE INDEX idx_site_participants_type ON site_participants(participant_type);
