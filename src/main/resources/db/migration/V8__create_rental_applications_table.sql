CREATE TABLE rental_applications (
                                     id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                     tenant_id BIGINT NOT NULL,
                                     property_id BIGINT NOT NULL,
                                     status VARCHAR(20) NOT NULL,
                                     reason TEXT NULL,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_rental_application_tenant
                                         FOREIGN KEY (tenant_id) REFERENCES users(id) ON DELETE CASCADE,

                                     CONSTRAINT fk_rental_application_property
                                         FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE
);
