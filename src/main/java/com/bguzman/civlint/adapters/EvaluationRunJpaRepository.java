package com.bguzman.civlint.adapters;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to stored runs.
 */
public interface EvaluationRunJpaRepository extends JpaRepository<EvaluationRunEntity, String> {

    List<EvaluationRunEntity> findAllByOrderByRunIdAsc();
}
