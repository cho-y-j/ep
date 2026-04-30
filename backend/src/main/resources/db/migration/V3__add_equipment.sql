CREATE TABLE equipment (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    vehicle_no VARCHAR(32),
    category VARCHAR(32) NOT NULL,
    model VARCHAR(100),
    manufacturer VARCHAR(100),
    year INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_equipment_supplier_id ON equipment(supplier_id);
CREATE INDEX idx_equipment_category ON equipment(category);
CREATE INDEX idx_equipment_vehicle_no ON equipment(vehicle_no);
