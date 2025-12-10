package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceCategory;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenancePriority;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceRequest;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceStatus;
import com.peterscode.rentalmanagementsystem.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    List<MaintenanceRequest> findByTenantOrderByRequestDateDesc(User tenant);

    List<MaintenanceRequest> findByStatusOrderByRequestDateDesc(MaintenanceStatus status);

    List<MaintenanceRequest> findByCategoryOrderByRequestDateDesc(MaintenanceCategory category);

    List<MaintenanceRequest> findByPriorityOrderByRequestDateDesc(MaintenancePriority priority);

    List<MaintenanceRequest> findByTenantAndStatusOrderByRequestDateDesc(User tenant, MaintenanceStatus status);

    List<MaintenanceRequest> findByTenantAndCategoryOrderByRequestDateDesc(User tenant, MaintenanceCategory category);

    List<MaintenanceRequest> findByTenantAndPriorityOrderByRequestDateDesc(User tenant, MaintenancePriority priority);

    @Query("SELECT mr FROM MaintenanceRequest mr WHERE mr.status IN :openStatuses ORDER BY mr.requestDate DESC")
    List<MaintenanceRequest> findOpenRequests(@Param("openStatuses") List<MaintenanceStatus> openStatuses);

    @Query("SELECT mr FROM MaintenanceRequest mr WHERE mr.tenant = :tenant AND mr.status IN :openStatuses ORDER BY mr.requestDate DESC")
    List<MaintenanceRequest> findOpenRequestsByTenant(@Param("tenant") User tenant, @Param("openStatuses") List<MaintenanceStatus> openStatuses);

    @Query("SELECT COUNT(mr) FROM MaintenanceRequest mr WHERE mr.status IN :openStatuses")
    Long countOpenRequests(@Param("openStatuses") List<MaintenanceStatus> openStatuses);

    @Query("SELECT COUNT(mr) FROM MaintenanceRequest mr WHERE mr.tenant = :tenant AND mr.status IN :openStatuses")
    Long countOpenRequestsByTenant(@Param("tenant") User tenant, @Param("openStatuses") List<MaintenanceStatus> openStatuses);

    @Query("SELECT mr FROM MaintenanceRequest mr ORDER BY mr.requestDate DESC")
    List<MaintenanceRequest> findAllByOrderByRequestDateDesc();

    Optional<MaintenanceRequest> findByIdAndTenant(Long id, User tenant);

    boolean existsByIdAndTenant(Long id, User tenant);
}