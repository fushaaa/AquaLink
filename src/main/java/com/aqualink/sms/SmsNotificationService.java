package com.aqualink.sms;

import com.aqualink.order.WaterOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SmsNotificationService {

    private final TwilioSmsClient smsClient;

    public SmsNotificationService(TwilioSmsClient smsClient) {
        this.smsClient = smsClient;
    }

    public void orderCreatedAfterCommit(String phoneE164, WaterOrder order) {
        if (phoneE164 == null || phoneE164.isBlank() || !smsClient.isOutboundConfigured()) {
            return;
        }

        Runnable notification = () -> smsClient.sendText(phoneE164,
                "AquaLink: your " + order.getQuantityL() + " L water order is received "
                        + "(" + order.getStatus() + "). Reply STATUS for your latest order.");

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notification.run();
                }
            });
        } else {
            notification.run();
        }
    }
}