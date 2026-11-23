package com.redenius.metrics.service.repository;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MetricPointBatchWriter {

    private static final String UPSERT_SQL = """
            INSERT INTO metric_point (metric_id, ts, value)
            VALUES (?, ?, ?)
            ON CONFLICT (metric_id, ts) DO UPDATE SET value = EXCLUDED.value
            """;

    private final JdbcTemplate jdbcTemplate;

    public MetricPointBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Last write wins, also within one call: with reWriteBatchedInserts the driver merges the batch into a
     * single multi-row INSERT, and Postgres rejects ON CONFLICT DO UPDATE touching the same row twice.
     */
    public void batchUpsert(List<PointRow> points) {
        Map<PointKey, PointRow> latest = new LinkedHashMap<>();
        for (PointRow point : points) {
            latest.put(new PointKey(point.metricId(), point.ts()), point);
        }
        List<PointRow> rows = new ArrayList<>(latest.values());

        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PointRow point = rows.get(i);
                ps.setLong(1, point.metricId());
                ps.setTimestamp(2, Timestamp.from(point.ts()));
                ps.setDouble(3, point.value());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private record PointKey(Long metricId, Instant ts) {}
}
