CREATE EXTENSION IF NOT EXISTS timescaledb;

-- The primary key already serves history queries, so the default ts index is skipped.
SELECT create_hypertable('metric_point', by_range('ts', INTERVAL '1 day'),
                         create_default_indexes => false, migrate_data => true);

ALTER TABLE metric_point SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'metric_id',
    timescaledb.compress_orderby = 'ts'
);

SELECT add_compression_policy('metric_point', INTERVAL '7 days');
SELECT add_retention_policy('metric_point', INTERVAL '${pointRetention}');
