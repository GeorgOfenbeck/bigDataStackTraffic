CREATE TABLE road_stats (
    road_id INT NOT NULL,
    window_start BIGINT NOT NULL,
    window_end BIGINT NOT NULL,
    avg_speed DOUBLE PRECISION NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (road_id, window_start)
);
// /opt/bitnami/postgresql/bin/psql "host=postgres-postgresql.postgres.svc.cluster.local port=5432 user=postgres password='X6s4OheQQe'"