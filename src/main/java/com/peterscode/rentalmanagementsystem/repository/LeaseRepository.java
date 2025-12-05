package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.lease.LeaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaseRepository extends JpaRepository<Lease, Long> {

    List<Lease> findByTenant_Id(Long tenantId);

    List<Lease> findByProperty_Id(Long propertyId);

    List<Lease> findByStatus(LeaseStatus status);


    List<Lease> findByProperty_IdAndStatus(Long propertyId, LeaseStatus status);
}
