package com.example.videopipeline.domain.job.repository;

import com.example.videopipeline.domain.job.entity.RebalanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebalanceEventRepository extends JpaRepository<RebalanceEvent, Long> {
}
