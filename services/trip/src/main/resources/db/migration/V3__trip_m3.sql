ALTER TABLE trips ADD COLUMN matching_deadline timestamptz;

UPDATE trips
SET matching_deadline = requested_at + interval '90 seconds'
WHERE matching_deadline IS NULL;

CREATE INDEX idx_trips_matching_deadline
    ON trips (matching_deadline) WHERE state IN ('requested', 'matching');
