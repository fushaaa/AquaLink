package com.aqualink.sms;

import com.aqualink.order.OrderStatus;
import com.aqualink.order.WaterOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsNotificationServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void skipsMissingRecipientOrUnconfiguredTwilio() {
        TwilioSmsClient smsClient = mock(TwilioSmsClient.class);
        SmsNotificationService service = new SmsNotificationService(smsClient);
        WaterOrder order = mock(WaterOrder.class);
        when(smsClient.isOutboundConfigured()).thenReturn(false);

        service.orderCreatedAfterCommit("+15551234567", order);
        service.orderCreatedAfterCommit(" ", order);
        service.orderCreatedAfterCommit(null, order);

        verify(smsClient, never()).sendText("+15551234567", "AquaLink: your 0 L water order is received (null). Reply STATUS for your latest order.");
    }

    @Test
    void sendsImmediatelyWhenNoTransactionIsActive() {
        TwilioSmsClient smsClient = mock(TwilioSmsClient.class);
        SmsNotificationService service = new SmsNotificationService(smsClient);
        WaterOrder order = mock(WaterOrder.class);
        when(smsClient.isOutboundConfigured()).thenReturn(true);
        when(order.getQuantityL()).thenReturn(1000);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);

        service.orderCreatedAfterCommit("+15551234567", order);

        verify(smsClient).sendText("+15551234567",
                "AquaLink: your 1000 L water order is received (PENDING). Reply STATUS for your latest order.");
    }

    @Test
    void defersSendUntilTransactionAfterCommit() {
        TwilioSmsClient smsClient = mock(TwilioSmsClient.class);
        SmsNotificationService service = new SmsNotificationService(smsClient);
        WaterOrder order = mock(WaterOrder.class);
        when(smsClient.isOutboundConfigured()).thenReturn(true);
        when(order.getQuantityL()).thenReturn(500);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        TransactionSynchronizationManager.initSynchronization();

        service.orderCreatedAfterCommit("+15551234567", order);

        verify(smsClient, never()).sendText(
                "+15551234567", "AquaLink: your 500 L water order is received (PENDING). Reply STATUS for your latest order.");
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(smsClient).sendText(
                "+15551234567", "AquaLink: your 500 L water order is received (PENDING). Reply STATUS for your latest order.");
    }
}