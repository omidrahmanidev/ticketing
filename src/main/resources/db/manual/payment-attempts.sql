-- MySQL 8 upgrade for EXISTING event-sourced databases, with all app instances stopped.
-- Back up first. Hibernate ddl-auto=update does not remove existing unique constraints.
-- Fresh databases already have these indexes and do not need this script.
-- Only reservation_id uniqueness is removed; payment/operation/event identities are preserved.
DELIMITER $$
CREATE PROCEDURE allow_payment_attempts()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE indexed_table VARCHAR(64);
    DECLARE unique_index VARCHAR(64);
    DECLARE reservation_uniques CURSOR FOR
        SELECT table_name, index_name FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name IN ('payment_streams', 'payments')
          AND non_unique = 0 AND index_name <> 'PRIMARY'
        GROUP BY table_name, index_name
        HAVING COUNT(*) = 1 AND MAX(column_name) = 'reservation_id';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;

    -- Add replacement indexes first, including for any reservation foreign keys.
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'payment_streams'
                     AND index_name = 'idx_payment_stream_reservation') THEN
        ALTER TABLE payment_streams ADD INDEX idx_payment_stream_reservation (reservation_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'payments'
                     AND index_name = 'idx_payments_reservation') THEN
        ALTER TABLE payments ADD INDEX idx_payments_reservation (reservation_id);
    END IF;

    -- Hibernate may have generated the projection constraint name; discover it by column.
    OPEN reservation_uniques;
    remove_uniques: LOOP
        FETCH reservation_uniques INTO indexed_table, unique_index;
        IF finished THEN LEAVE remove_uniques; END IF;
        SET @payment_attempt_ddl = CONCAT('ALTER TABLE `', indexed_table, '` DROP INDEX `',
                                         REPLACE(unique_index, '`', '``'), '`');
        PREPARE payment_attempt_statement FROM @payment_attempt_ddl;
        EXECUTE payment_attempt_statement;
        DEALLOCATE PREPARE payment_attempt_statement;
    END LOOP;
    CLOSE reservation_uniques;
END$$
DELIMITER ;
CALL allow_payment_attempts();
DROP PROCEDURE allow_payment_attempts;
