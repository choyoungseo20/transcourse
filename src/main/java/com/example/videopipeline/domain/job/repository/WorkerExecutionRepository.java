package com.example.videopipeline.domain.job.repository;

import com.example.videopipeline.domain.job.entity.WorkerExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerExecutionRepository extends JpaRepository<WorkerExecution, Long> {
}
