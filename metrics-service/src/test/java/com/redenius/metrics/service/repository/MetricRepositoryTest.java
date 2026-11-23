package com.redenius.metrics.service.repository;

import com.redenius.metrics.service.BaseIntegrationTest;
import com.redenius.metrics.service.entity.Metric;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MetricRepositoryTest extends BaseIntegrationTest {

    @Autowired
    MetricRepository metricRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MetricPointBatchWriter batchWriter;

    @Test
    @Transactional
    void shouldSaveAndFindMetric() {
        Metric metric = new Metric("cpu.usage", "%", "host-01");
        Metric saved = metricRepository.save(metric);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Metric> found = metricRepository.findByNameAndHost("cpu.usage", "host-01");
        assertThat(found).isPresent();
        assertThat(found.get().getUnit()).isEqualTo("%");
    }

    @Test
    void shouldNotKeepIndexRedundantWithPointPrimaryKey() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'metric_point'", String.class);
        assertThat(indexes).containsExactly("metric_point_pkey");
    }

    @Test
    void pointTableShouldBeCompressedHypertableWithRetention() {
        Integer hypertables = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM timescaledb_information.hypertables
                WHERE hypertable_name = 'metric_point' AND compression_enabled
                """, Integer.class);
        List<String> jobs = jdbcTemplate.queryForList("""
                SELECT proc_name FROM timescaledb_information.jobs
                WHERE hypertable_name = 'metric_point' ORDER BY proc_name
                """, String.class);
        String dropAfter = jdbcTemplate.queryForObject("""
                SELECT config ->> 'drop_after' FROM timescaledb_information.jobs
                WHERE hypertable_name = 'metric_point' AND proc_name = 'policy_retention'
                """, String.class);

        assertThat(hypertables).isEqualTo(1);
        assertThat(jobs).containsExactly("policy_compression", "policy_retention");
        assertThat(dropAfter).isEqualTo("90 days");
    }

    @Test
    void retentionShouldDropOldChunksOnly() {
        Metric metric = metricRepository.save(new Metric("retention.test", "ms", "retention-host"));
        jdbcTemplate.update("""
                INSERT INTO metric_point (metric_id, ts, value)
                VALUES (?, now() - INTERVAL '200 days', 1), (?, now() - INTERVAL '1 hour', 2)
                """, metric.getId(), metric.getId());

        jdbcTemplate.queryForList(
                "SELECT drop_chunks('metric_point', older_than => INTERVAL '90 days')", String.class);

        List<Double> remaining = jdbcTemplate.queryForList(
                "SELECT value FROM metric_point WHERE metric_id = ?", Double.class, metric.getId());
        assertThat(remaining).containsExactly(2.0);
    }

    @Test
    void shouldUpsertIntoCompressedChunk() {
        Long metricId = metricRepository.save(new Metric("compressed.test", "ms", "compressed-host")).getId();
        Instant t0 = Instant.parse("2023-03-10T12:00:00Z");
        Instant t1 = t0.plusSeconds(60);
        Instant t2 = t0.plusSeconds(120);
        batchWriter.batchUpsert(List.of(new PointRow(metricId, t0, 1.0), new PointRow(metricId, t1, 2.0)));

        jdbcTemplate.queryForList("""
                SELECT compress_chunk(c) FROM show_chunks('metric_point',
                    newer_than => TIMESTAMPTZ '2023-03-10 00:00+00', older_than => TIMESTAMPTZ '2023-03-11 00:00+00') c
                """, String.class);
        Boolean compressed = jdbcTemplate.queryForObject("""
                SELECT bool_and(is_compressed) FROM timescaledb_information.chunks
                WHERE hypertable_name = 'metric_point' AND range_start = TIMESTAMPTZ '2023-03-10 00:00+00'
                """, Boolean.class);
        assertThat(compressed).isTrue();

        batchWriter.batchUpsert(List.of(new PointRow(metricId, t0, 5.0), new PointRow(metricId, t2, 3.0)));

        List<Double> values = jdbcTemplate.queryForList(
                "SELECT value FROM metric_point WHERE metric_id = ? ORDER BY ts", Double.class, metricId);
        assertThat(values).containsExactly(5.0, 2.0, 3.0);
    }

    @Test
    @Transactional
    void shouldReturnEmptyForUnknownMetric() {
        Optional<Metric> found = metricRepository.findByNameAndHost("nonexistent", "nowhere");
        assertThat(found).isEmpty();
    }
}
