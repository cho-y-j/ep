CREATE TABLE persons (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    birth DATE,
    phone VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_persons_supplier_id ON persons(supplier_id);
CREATE INDEX idx_persons_name ON persons(name);

CREATE TABLE person_roles (
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (person_id, role)
);

CREATE INDEX idx_person_roles_role ON person_roles(role);
