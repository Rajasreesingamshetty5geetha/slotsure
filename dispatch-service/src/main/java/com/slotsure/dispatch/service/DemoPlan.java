package com.slotsure.dispatch.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The demo bookings for a date, derived only from the date. order-service and payment-service keep
 * identical copies, so the demo assignments here belong to the same order ids, areas, hours and agents
 * as order-service's demo orders, and "Mark delivered" on a demo order works. Change all three copies together;
 * DemoPlanTest pins a fingerprint of the output.
 */
final class DemoPlan {

    record Agent(String agentId, String name, String phone, String homeArea) {
    }

    /** One demo booking. choice is PREPAID, ONLINE or CASH. random may be drawn from further, per service. */
    record Booking(int index, String orderId, String areaName, int startHour, boolean failed, Agent agent,
                   int deliveredMinute, int itemIndex, BigDecimal amount, String choice, int bookedMinutesBefore,
                   Random random) {
    }

    static final List<String> AREAS = List.of("Koramangala", "HSR Layout", "Indiranagar", "BTM Layout", "Jayanagar",
            "Whitefield", "Marathahalli", "Electronic City", "Malleshwaram", "Hebbal");
    private static final double[] AREA_WEIGHTS = {1.4, 1.3, 1.3, 0.9, 0.9, 1.2, 1.0, 0.8, 0.7, 0.6};
    static final int FIRST_HOUR = 8;
    static final int LAST_HOUR = 22;
    private static final double FAILED_SHARE = 0.05;
    private static final double COD_SHARE = 0.35;
    private static final double CASH_SHARE_OF_COD = 0.70;

    /** Prices of order-service's PurchaseService.CATALOG, in the same order. */
    static final List<BigDecimal> ITEM_PRICES = List.of(
            new BigDecimal("2499.00"), new BigDecimal("4999.00"), new BigDecimal("1999.00"), new BigDecimal("13999.00"),
            new BigDecimal("5499.00"), new BigDecimal("3799.00"), new BigDecimal("1499.00"), new BigDecimal("3299.00"),
            new BigDecimal("1799.00"), new BigDecimal("7999.00"), new BigDecimal("1299.00"), new BigDecimal("899.00"));

    static final List<Agent> AGENTS = List.of(
            agent(0, "Ravi Kumar", "+91 98450 11201", "Koramangala"),
            agent(1, "Suresh Gowda", "+91 98450 11202", "Koramangala"),
            agent(2, "Manjunath R", "+91 98450 11203", "HSR Layout"),
            agent(3, "Arjun Reddy", "+91 98450 11204", "Indiranagar"),
            agent(4, "Kiran Shetty", "+91 98450 11205", "BTM Layout"),
            agent(5, "Prakash Naik", "+91 98450 11206", "Jayanagar"),
            agent(6, "Imran Pasha", "+91 98450 11207", "Whitefield"),
            agent(7, "Vinod Rao", "+91 98450 11208", "Whitefield"),
            agent(8, "Santosh Kulkarni", "+91 98450 11209", "Marathahalli"),
            agent(9, "Naveen Hegde", "+91 98450 11210", "Electronic City"),
            agent(10, "Deepak Murthy", "+91 98450 11211", "Malleshwaram"),
            agent(11, "Rahul Sharma", "+91 98450 11212", "Hebbal"));

    private DemoPlan() {
    }

    static List<Booking> forDate(LocalDate date, int minPerDay, int maxPerDay) {
        int count = minPerDay + new Random(seed("slotsure-demo-day:" + date)).nextInt(Math.max(0, maxPerDay - minPerDay) + 1);
        double[] hourWeights = new double[LAST_HOUR - FIRST_HOUR];
        for (int i = 0; i < hourWeights.length; i++) {
            hourWeights[i] = hourWeight(FIRST_HOUR + i);
        }
        List<Booking> bookings = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            // The order of these draws is part of the plan: never reorder them.
            Random random = new Random(seed("slotsure-demo-booking:" + date + ":" + n));
            String area = AREAS.get(pick(AREA_WEIGHTS, random.nextDouble()));
            int hour = FIRST_HOUR + pick(hourWeights, random.nextDouble());
            boolean failed = random.nextDouble() < FAILED_SHARE;
            int agentPick = random.nextInt(1_000);
            int deliveredMinute = 10 + random.nextInt(46);
            int itemIndex = random.nextInt(ITEM_PRICES.size());
            boolean cod = random.nextDouble() < COD_SHARE;
            boolean cash = random.nextDouble() < CASH_SHARE_OF_COD;
            int bookedMinutesBefore = 60 + random.nextInt(30 * 60);
            String choice = !cod ? "PREPAID" : cash ? "CASH" : "ONLINE";
            bookings.add(new Booking(n, orderId(date, n), area, hour, failed, agentFor(area, agentPick),
                    deliveredMinute, itemIndex, ITEM_PRICES.get(itemIndex), choice, bookedMinutesBefore, random));
        }
        return bookings;
    }

    static String orderId(LocalDate date, int index) {
        return uuid("slotsure-demo-order:" + date + ":" + index);
    }

    static String customerId(LocalDate date, int index) {
        return uuid("slotsure-demo-customer:" + date + ":" + index);
    }

    static String uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * A well-mixed seed per name. Consecutive numeric seeds would not do: java.util.Random gives almost
     * the same first values for neighbouring seeds, which put a whole day of bookings in one area.
     */
    static long seed(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
    }

    /** Evening peak 18:00-21:00, shoulders either side, a small lunch bump. */
    static double hourWeight(int startHour) {
        if (startHour >= 18 && startHour < 21) {
            return 3.0;
        }
        if (startHour == 17 || startHour == 21) {
            return 1.5;
        }
        if (startHour == 12 || startHour == 13) {
            return 1.2;
        }
        return 0.7;
    }

    private static Agent agentFor(String area, int agentPick) {
        List<Agent> home = AGENTS.stream().filter(agent -> agent.homeArea().equals(area)).toList();
        List<Agent> pool = home.isEmpty() ? AGENTS : home;
        return pool.get(agentPick % pool.size());
    }

    private static int pick(double[] weights, double roll) {
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double point = roll * total;
        double running = 0;
        for (int i = 0; i < weights.length; i++) {
            running += weights[i];
            if (point < running) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static Agent agent(int index, String name, String phone, String homeArea) {
        return new Agent(uuid("slotsure-agent:" + index), name, phone, homeArea);
    }
}
