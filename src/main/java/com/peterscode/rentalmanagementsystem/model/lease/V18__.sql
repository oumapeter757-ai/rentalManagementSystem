CREATE TABLE leases
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id    BIGINT         NOT NULL,
    property_id  BIGINT         NOT NULL,
    start_date   date           NOT NULL,
    end_date     date           NOT NULL,
    monthly_rent DECIMAL(19, 2) NOT NULL,
    deposit      DECIMAL(19, 2) NULL,
    status       VARCHAR(255)   NOT NULL,
    notes        TEXT NULL,
    created_at   datetime       NOT NULL,
    updated_at   datetime       NOT NULL,
    CONSTRAINT pk_leases PRIMARY KEY (id)
);

ALTER TABLE leases
    ADD CONSTRAINT FK_LEASES_ON_PROPERTY FOREIGN KEY (property_id) REFERENCES properties (id);

ALTER TABLE leases
    ADD CONSTRAINT FK_LEASES_ON_TENANT FOREIGN KEY (tenant_id) REFERENCES users (id);