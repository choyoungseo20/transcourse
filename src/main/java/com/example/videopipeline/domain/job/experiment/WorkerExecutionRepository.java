package com.example.videopipeline.domain.job.experiment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerExecutionRepository extends JpaRepository<WorkerExecution, Long> {
}
