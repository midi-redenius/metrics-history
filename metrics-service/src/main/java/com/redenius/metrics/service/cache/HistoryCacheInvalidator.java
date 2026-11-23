package com.redenius.metrics.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HistoryCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(HistoryCacheInvalidator.class);

    private final HistoryCacheGenerations generations;

    public HistoryCacheInvalidator(HistoryCacheGenerations generations) {
        this.generations = generations;
    }

    /**
     * Runs after commit so a concurrent reader cannot re-cache pre-commit data under the new generation.
     * The points are already committed at this point, so a cache failure must not fail the ingest.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPointsIngested(MetricPointsIngestedEvent event) {
        try {
            generations.bump(event.metricId());
        } catch (RuntimeException ex) {
            log.warn("Failed to invalidate history cache for metricId={}; stale entries expire by TTL",
                    event.metricId(), ex);
        }
    }
}
