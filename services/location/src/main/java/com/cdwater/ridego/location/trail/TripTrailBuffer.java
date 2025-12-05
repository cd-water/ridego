package com.cdwater.ridego.location.trail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TripTrailBuffer {

    private static final Logger log = LoggerFactory.getLogger(TripTrailBuffer.class);

    private final BlockingQueue<TripPosition> queue = new ArrayBlockingQueue<>(200_000);
    private final AtomicLong dropped = new AtomicLong();
    private final JdbcTemplate jdbc;

    public TripTrailBuffer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(TripPosition position) {
        if (!queue.offer(position)) {
            dropped.incrementAndGet();
        }
    }

    @Scheduled(fixedDelayString = "${ridego.trail.flush-ms:1000}")
    public void flush() {
        List<TripPosition> batch = new ArrayList<>(1000);
        queue.drainTo(batch, 1000);
        long droppedNow = dropped.getAndSet(0);
        if (droppedNow > 0) {
            log.warn("trail buffer overflow, dropped {} positions", droppedNow);
        }
        if (batch.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                        INSERT INTO trip_positions (trip_id, driver_id, city_id, lat, lng, recorded_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                batch, batch.size(),
                (ps, p) -> {
                    ps.setObject(1, p.tripId());
                    ps.setString(2, p.driverId());
                    ps.setString(3, p.cityId());
                    ps.setDouble(4, p.lat());
                    ps.setDouble(5, p.lng());
                    ps.setTimestamp(6, Timestamp.from(p.recordedAt()));
                });
    }
}
