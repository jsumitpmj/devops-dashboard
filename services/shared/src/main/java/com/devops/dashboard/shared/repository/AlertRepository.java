package com.devops.dashboard.shared.repository;

import com.devops.dashboard.shared.model.Alert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByStatusOrderByCreatedAtDesc(Alert.AlertStatus status, Pageable pageable);

    List<Alert> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    List<Alert> findByDeploymentIdOrderByCreatedAtDesc(UUID deploymentId);

    List<Alert> findByEnvironmentIdOrderByCreatedAtDesc(UUID environmentId);

    List<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
