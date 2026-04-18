package com.devops.dashboard.shared.repository;

import com.devops.dashboard.shared.model.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByProjectIdOrderByName(UUID projectId);

    List<Environment> findByProjectIdAndEnvType(UUID projectId, Environment.EnvironmentType envType);
}
