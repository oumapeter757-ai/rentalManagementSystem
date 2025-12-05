package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.application.RentalApplication;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;
import com.peterscode.rentalmanagementsystem.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RentalApplicationRepository extends JpaRepository<RentalApplication, Long> {

    List<RentalApplication> findByTenant(User tenant);

    List<RentalApplication> findByPropertyId(Long propertyId);

    List<RentalApplication> findByPropertyOwner(User owner);

    List<RentalApplication> findByStatus(RentalApplicationStatus status);

    Optional<RentalApplication> findByPropertyIdAndTenantId(Long propertyId, Long tenantId);

    @Query("SELECT ra FROM RentalApplication ra WHERE ra.property.owner = :owner AND ra.status = :status")
    List<RentalApplication> findByOwnerAndStatus(@Param("owner") User owner,
                                                 @Param("status") RentalApplicationStatus status);

    long countByStatus(RentalApplicationStatus status);

    @Query("SELECT COUNT(ra) FROM RentalApplication ra WHERE ra.property.owner = :owner")
    long countByPropertyOwner(@Param("owner") User owner);

    @Query("SELECT ra FROM RentalApplication ra WHERE ra.tenant = :tenant AND ra.status = :status")
    List<RentalApplication> findByTenantAndStatus(@Param("tenant") User tenant,
                                                  @Param("status") RentalApplicationStatus status);
}