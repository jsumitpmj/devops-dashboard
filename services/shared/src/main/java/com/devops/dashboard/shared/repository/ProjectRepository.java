package com.devops.dashboard.shared.repository;

import com.devops.dashboard.shared.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByIsArchivedFalseOrderByCreatedAtDesc();

    Optional<Project> findBySlug(String slug);

    List<Project> findByOwnerId(UUID ownerId);
}
