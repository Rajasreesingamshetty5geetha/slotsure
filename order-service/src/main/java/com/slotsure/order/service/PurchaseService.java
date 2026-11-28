package com.slotsure.order.service;

import com.slotsure.order.config.OrderProperties;
import com.slotsure.order.dto.PurchaseResponse;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.PurchaseStatus;
import com.slotsure.order.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Purchases come from checkout, which SlotSure does not run, so a customer without any gets
 * three starter purchases on first look (one PREPAID, two COD) to book slots for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    public record CatalogItem(String name, String category, BigDecimal amount) {
    }

    public static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem("Wireless Headphones", "Electronics", new BigDecimal("2499.00")),
            new CatalogItem("Smart Watch", "Electronics", new BigDecimal("4999.00")),
            new CatalogItem("Bluetooth Speaker", "Electronics", new BigDecimal("1999.00")),
            new CatalogItem("Kindle Paperwhite", "Electronics", new BigDecimal("13999.00")),
            new CatalogItem("Air Fryer 4L", "Home & Kitchen", new BigDecimal("5499.00")),
            new CatalogItem("Mixer Grinder 750W", "Home & Kitchen", new BigDecimal("3799.00")),
            new CatalogItem("Cotton Bedsheet Set", "Home & Kitchen", new BigDecimal("1499.00")),
            new CatalogItem("Running Shoes", "Fashion", new BigDecimal("3299.00")),
            new CatalogItem("Laptop Backpack", "Fashion", new BigDecimal("1799.00")),
            new CatalogItem("Ergonomic Office Chair", "Furniture", new BigDecimal("7999.00")),
            new CatalogItem("Basmati Rice 10 kg", "Grocery", new BigDecimal("1299.00")),
            new CatalogItem("Yoga Mat", "Sports", new BigDecimal("899.00")));

    private static final List<PaymentMode> STARTER_MODES = List.of(PaymentMode.PREPAID, PaymentMode.COD, PaymentMode.COD);
    private static final int MIN_DEMO_PREPAID = 1;
    private static final int MIN_DEMO_COD = 2;

    private final PurchaseRepository purchaseRepository;
    private final TransactionTemplate transactionTemplate;
    private final OrderProperties properties;

    public List<PurchaseResponse> listForCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        List<Purchase> purchases = purchaseRepository.findByCustomerIdOrderByPlacedAtDesc(customerId);
        if (purchases.isEmpty()) {
            createStarterPurchases(customerId);
            purchases = purchaseRepository.findByCustomerIdOrderByPlacedAtDesc(customerId);
        }
        return purchases.stream().map(PurchaseResponse::of).toList();
    }

    /**
     * Gives each demo customer (CONTRACT V2.2) something to book: at least one PREPAID and two COD
     * purchases waiting for a slot. Runs on startup, so a used-up demo account is topped up on restart.
     */
    public void topUpDemoCustomers() {
        for (String customerId : properties.demoData().customers()) {
            if (purchaseRepository.findByCustomerIdOrderByPlacedAtDesc(customerId).isEmpty()) {
                createStarterPurchases(customerId);
                continue;
            }
            Random random = new Random();
            int base = random.nextInt(CATALOG.size());
            List<Purchase> extra = new ArrayList<>();
            long prepaid = purchaseRepository.countByCustomerIdAndPaymentModeAndStatus(customerId,
                    PaymentMode.PREPAID, PurchaseStatus.AWAITING_SLOT);
            for (long i = prepaid; i < MIN_DEMO_PREPAID; i++) {
                extra.add(newPurchase(UUID.randomUUID().toString(), customerId, PaymentMode.PREPAID, base, extra.size(), random));
            }
            long cod = purchaseRepository.countByCustomerIdAndPaymentModeAndStatus(customerId,
                    PaymentMode.COD, PurchaseStatus.AWAITING_SLOT);
            for (long i = cod; i < MIN_DEMO_COD; i++) {
                extra.add(newPurchase(UUID.randomUUID().toString(), customerId, PaymentMode.COD, base, extra.size(), random));
            }
            if (!extra.isEmpty()) {
                transactionTemplate.executeWithoutResult(status -> purchaseRepository.saveAll(extra));
                log.info("Topped up demo customer {} with {} purchases awaiting a slot", customerId, extra.size());
            }
        }
    }

    /**
     * The ids are derived from the customer id, so two first requests racing each other insert the
     * same rows: the loser hits the primary key and simply reads what the winner wrote.
     */
    void createStarterPurchases(String customerId) {
        Random random = new Random(customerId.hashCode());
        int base = random.nextInt(CATALOG.size());
        List<Purchase> starters = new ArrayList<>();
        for (int i = 0; i < STARTER_MODES.size(); i++) {
            String id = UUID.nameUUIDFromBytes((customerId + ":starter:" + i).getBytes(StandardCharsets.UTF_8)).toString();
            starters.add(newPurchase(id, customerId, STARTER_MODES.get(i), base, i, random));
        }
        try {
            transactionTemplate.executeWithoutResult(status -> purchaseRepository.saveAllAndFlush(starters));
            log.info("Created {} starter purchases for customer {}", starters.size(), customerId);
        } catch (DataIntegrityViolationException e) {
            log.info("Starter purchases for customer {} were created by a concurrent request", customerId);
        }
    }

    /** A purchase placed a few hours to a day ago, with an item not already used in this batch. */
    private static Purchase newPurchase(String id, String customerId, PaymentMode mode, int base, int position,
                                        Random random) {
        // Steps of 5 through 12 items never repeat within a batch of three.
        CatalogItem item = CATALOG.get((base + position * 5) % CATALOG.size());
        Instant placedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                .minus(Duration.ofMinutes(30L + position * 90L + random.nextInt(600)));
        return new Purchase(id, customerId, item.name(), item.category(), item.amount(), mode, placedAt);
    }
}
