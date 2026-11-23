package com.redenius.metrics.service.repository;

import com.redenius.metrics.service.entity.Metric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface MetricRepository extends JpaRepository<Metric, Long> {

    Optional<Metric> findByNameAndHost(String name, String host);

    Page<Metric> findByHost(String host, Pageable pageable);

    Page<Metric> findByName(String name, Pageable pageable);

    Page<Metric> findByNameAndHost(String name, String host, Pageable pageable);

    @Query("SELECT m.id FROM Metric m WHERE m.id IN :ids")
    Set<Long> findExistingIds(Collection<Long> ids);
}
