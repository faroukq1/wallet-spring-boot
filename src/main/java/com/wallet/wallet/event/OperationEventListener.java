package com.wallet.wallet.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OperationEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOperationCompleted(OperationCompletedEvent event) {

        System.out.println("========== AUDIT LOG ==========");
        System.out.println("Operation   : " + event.operationType());
        System.out.println("Source      : " + event.sourceAccountId());
        System.out.println("Destination : " + event.destinationAccountId());
        System.out.println("Amount      : " + event.amount());
        System.out.println("CompletedAt : " + event.completedAt());
        System.out.println("Thread      : " + Thread.currentThread().getName());
        System.out.println("================================");

        String recipient = event.destinationAccountId() != null
                ? "account " + event.destinationAccountId()
                : "account " + event.sourceAccountId();

        System.out.println("Notification sent to " + recipient
                + " for " + event.operationType()
                + " of " + event.amount());
    }
}