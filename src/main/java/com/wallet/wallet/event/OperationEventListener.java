package com.wallet.wallet.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OperationEventListener {

    private static final Logger log = LoggerFactory.getLogger(OperationEventListener.class);

    /**
     * Runs only AFTER the main transaction committed and on the dedicated
     * async executor, so audit + notification never block the HTTP thread and
     * never fire for an operation that was rolled back.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOperationCompleted(OperationCompletedEvent event) {
        log.info("AUDIT operation_type={} source_account={} destination_account={} amount={} completed_at={} thread={}",
                event.operationType(), event.sourceAccountId(), event.destinationAccountId(),
                event.amount(), event.completedAt(), Thread.currentThread().getName());

        String recipient = event.destinationAccountId() != null
                ? "account " + event.destinationAccountId()
                : "account " + event.sourceAccountId();

        log.info("Notification sent to {} for {} of {}", recipient, event.operationType(), event.amount());
    }
}
