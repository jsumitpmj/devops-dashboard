package com.devops.dashboard.shared.repository;

import com.devops.dashboard.shared.model.Deployment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    List<Deployment> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, Deployment.DeploymentStatus status, Pageable pageable);

    List<Deployment> findByEnvironmentIdOrderByCreatedAtDesc(UUID environmentId, Pageable pageable);

    @Query("SELECT d FROM Deployment d WHERE d.status = :status ORDER BY d.createdAt DESC")
    List<Deployment> findByStatusOrderByCreatedAtDesc(Deployment.DeploymentStatus status, Pageable pageable);

    @Query("SELECT d FROM Deployment d WHERE d.status = 'in_progress' AND d.startedAt < :cutoff")
    List<Deployment> findStaleDeployments(OffsetDateTime cutoff, Pageable pageable);

    @Query("SELECT COUNT(d) FROM Deployment d WHERE d.status = 'pending'")
    long countPending();
}
