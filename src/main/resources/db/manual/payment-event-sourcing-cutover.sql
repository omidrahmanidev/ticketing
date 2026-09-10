-- ONE-TIME MySQL 8 cutover for an EXISTING pre-refactor database, with all app instances stopped.
-- Back up the database first. DDL in MySQL commits implicitly; do not use --force.
-- Fresh databases use Hibernate's existing ddl-auto=update and must NOT run this script.
-- Historical events and payment rows are preserved in *_legacy. No history is discarded.
DELIMITER $$
CREATE PROCEDURE assert_payment_cutover_ready()
BEGIN
    IF EXISTS (SELECT 1 FROM payments GROUP BY reservation_id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Multiple legacy payments per reservation: reconcile identities before cutover';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
               AND table_name IN ('payment_streams', 'payments_legacy', 'payment_events_legacy')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cutover already started; restore backup or inspect before proceeding';
    END IF;
END$$
DELIMITER ;
CALL assert_payment_cutover_ready();
DROP PROCEDURE assert_payment_cutover_ready;

CREATE TABLE payments_legacy LIKE payments;
INSERT INTO payments_legacy SELECT * FROM payments;
RENAME TABLE payment_events TO payment_events_legacy;

CREATE TABLE payment_streams (
    payment_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    INDEX idx_payment_stream_reservation (reservation_id)
);
CREATE TABLE payment_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_events_payment_sequence UNIQUE (payment_id, sequence_number)
);
INSERT INTO payment_streams (payment_id, reservation_id)
SELECT payment_id, reservation_id FROM payments_legacy;

SET @payment_cutover_at = CURRENT_TIMESTAMP(6);
INSERT INTO payment_events (payment_id, sequence_number, event_type, payload, created_at)
SELECT payment_id, 1, 'PaymentInitiated', JSON_OBJECT(
    'paymentId', payment_id, 'reservationId', reservation_id, 'userId', user_id,
    'amount', amount, 'operationId', operation_id,
    'occurredAt', DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f')), created_at
FROM payments_legacy;

-- Active legacy calls might have reached the provider. Reconcile before attempting anything again.
-- Exact old retry timestamps/provider outcomes are absent from the audit history, hence this explicit checkpoint.
INSERT INTO payment_events (payment_id, sequence_number, event_type, payload, created_at)
SELECT payment_id, 2, 'PaymentLegacyStateImported', JSON_OBJECT(
    'state', JSON_OBJECT(
        'paymentId', payment_id, 'reservationId', reservation_id, 'userId', user_id,
        'amount', amount, 'operationId', operation_id, 'providerReference', provider_reference,
        'status', CASE WHEN status IN ('INIT_PENDING','AWAITING_CONFIRM','REFUND_PENDING','INQUIRY_PENDING')
                       THEN 'INQUIRY_PENDING' ELSE status END,
        'targetOperation', CASE status WHEN 'INIT_PENDING' THEN 'INIT' WHEN 'AWAITING_CONFIRM' THEN 'CONFIRM'
                           WHEN 'REFUND_PENDING' THEN 'REFUND' ELSE target_operation END,
        'retryCount', retry_count,
        'nextRetryAt', CASE WHEN status IN ('INIT_PENDING','AWAITING_CONFIRM','REFUND_PENDING','INQUIRY_PENDING')
                            THEN DATE_FORMAT(@payment_cutover_at, '%Y-%m-%dT%H:%i:%s.%f') ELSE NULL END,
        'lastError', last_error, 'version', 2,
        'createdAt', DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f'),
        'updatedAt', DATE_FORMAT(@payment_cutover_at, '%Y-%m-%dT%H:%i:%s.%f'),
        'requestId', NULL, 'requestedOperation', NULL, 'dispatched', FALSE),
    'occurredAt', DATE_FORMAT(@payment_cutover_at, '%Y-%m-%dT%H:%i:%s.%f')), @payment_cutover_at
FROM payments_legacy;

ALTER TABLE payments
    MODIFY payment_id BIGINT NOT NULL,
    ADD COLUMN request_id VARCHAR(255) NULL,
    ADD COLUMN requested_operation VARCHAR(30) NULL,
    ADD COLUMN dispatched BIT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events
    ADD COLUMN available_at DATETIME(6) NULL,
    ADD COLUMN delivery_attempts INT NULL;
-- The projection is now disposable. Rebuild from events at the first controlled startup:
-- --app.scheduling.enabled=false --payment.projections.rebuild-on-startup=true
DELETE FROM payments;
