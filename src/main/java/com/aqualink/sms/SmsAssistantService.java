package com.aqualink.sms;

import com.aqualink.household.Household;
import com.aqualink.household.HouseholdRepository;
import com.aqualink.order.WaterOrder;
import com.aqualink.order.WaterOrderRepository;
import com.aqualink.order.WaterPoolingService;
import com.aqualink.user.User;
import com.aqualink.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

@Service
public class SmsAssistantService {

    private static final String ORDER_HELP = "To order: ORDER <litres> <start-UTC> <end-UTC>. "
            + "Example: ORDER 1000 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z.";

    private final UserRepository users;
    private final HouseholdRepository households;
    private final WaterOrderRepository orders;
    private final WaterPoolingService poolingService;
    private final AiSmsAssistant aiAssistant;

    public SmsAssistantService(
            UserRepository users,
            HouseholdRepository households,
            WaterOrderRepository orders,
            WaterPoolingService poolingService,
            AiSmsAssistant aiAssistant) {
        this.users = users;
        this.households = households;
        this.orders = orders;
        this.poolingService = poolingService;
        this.aiAssistant = aiAssistant;
    }

    public String handleMessage(String from, String body, String messageSid) {
        if (body == null || body.isBlank()) {
            return "I didn't receive a message. Reply HELP for water-ordering instructions.";
        }
        if (body.length() > 1600) {
            return "That message is too long. Reply HELP for water-ordering instructions.";
        }

        String[] parts = body.trim().split("\\s+");
        String command = parts[0].toUpperCase(Locale.ROOT);
        if (command.equals("HELP")) {
            return aiAssistant.answer("Help me use AquaLink to order water.");
        }
        if (command.equals("ORDER")) {
            return placeOrder(from, parts, messageSid);
        }
        if (command.equals("STATUS")) {
            return latestOrderStatus(from);
        }
        return aiAssistant.answer(body.trim());
    }

    private String placeOrder(String from, String[] parts, String messageSid) {
        if (parts.length != 4) {
            return ORDER_HELP;
        }
        if (messageSid == null || messageSid.isBlank() || messageSid.length() > 64) {
            return "I couldn't safely identify that message for retry protection. Please send your order again.";
        }

        User user = users.findByPhoneE164(from).orElse(null);
        if (user == null) {
            return "This phone number is not linked to an AquaLink account. Contact your community manager.";
        }
        Household household = households.findPrimaryByMemberUserId(user.getId()).orElse(null);
        if (household == null) {
            return "No household is linked to your AquaLink account. Contact your community manager.";
        }

        try {
            int quantityL = Integer.parseInt(parts[1]);
            if (quantityL < 1 || quantityL > 50000) {
                return "Order quantity must be between 1 and 50000 litres. " + ORDER_HELP;
            }
            Instant start = Instant.parse(parts[2]);
            Instant end = Instant.parse(parts[3]);
            WaterOrder order = poolingService.createSmsRequest(
                    user.getId(), UUID.fromString(household.getPublicId()), quantityL,
                    start, end, "sms-" + messageSid, "Placed by SMS");
            return "AquaLink: your " + order.getQuantityL() + " L order is received ("
                    + order.getStatus() + "). Reply STATUS for the latest order.";
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            return "I couldn't place that order. Check the quantity and future UTC delivery times. " + ORDER_HELP;
        }
    }

    private String latestOrderStatus(String from) {
        User user = users.findByPhoneE164(from).orElse(null);
        if (user == null) {
            return "This phone number is not linked to an AquaLink account. Contact your community manager.";
        }
        Household household = households.findPrimaryByMemberUserId(user.getId()).orElse(null);
        if (household == null) {
            return "No household is linked to your AquaLink account. Contact your community manager.";
        }
        return orders.findTopByHouseholdIdOrderByCreatedAtDesc(household.getId())
                .map(order -> "Latest order: " + order.getQuantityL() + " L, " + order.getStatus()
                        + ", delivery window " + order.getWindowStart() + " to " + order.getWindowEnd() + ".")
                .orElse("There are no water orders for your household yet.");
    }
}