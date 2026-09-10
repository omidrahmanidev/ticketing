package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ro.midra.ticketing.payment.application.port.PaymentGateway;
import ro.midra.ticketing.payment.domain.PaymentOperation;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandWorker {
    private final PaymentStepTransactions transactions;
    private final PaymentGateway gateway;

    /** NEVER also rejects accidental transactional callers instead of silently suspending them. */
    @Transactional(propagation = Propagation.NEVER)
    public void execute(PaymentCommand command) {
        var state = transactions.prepare(command);
        if (state == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Payment gateway must run outside a database transaction");
        }
        var target = command.operation() == PaymentOperation.INQUIRY ? state.targetOperation() : command.operation();
        String key = state.operationId() + "-" + target;
        PaymentGateway.GatewayResult result;
        try {
            result = switch (command.operation()) {
                case INIT -> gateway.init(key, state.amount(), command.scenarioHint());
                case CONFIRM -> gateway.confirm(key, state.providerReference());
                case REFUND -> gateway.refund(key, state.providerReference(), state.amount());
                case INQUIRY -> gateway.inquiry(key, state.providerReference());
            };
        } catch (RuntimeException ex) {
            log.warn("paymentId={} operation={} provider call failed; reconciling outcome", command.paymentId(), command.operation(), ex);
            result = new PaymentGateway.GatewayResult(PaymentGateway.Outcome.UNKNOWN, state.providerReference(), "Gateway exception; outcome unknown");
        }
        // Result persistence failures deliberately escape: the committed dispatch remains recoverable.
        transactions.complete(command, result);
    }
}
