package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.domain.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class FakeDataStore {
    final List<Category> categories = new ArrayList<>();
    final Map<Long, Product> products = new HashMap<>();
    final Map<Integer, Order> openOrdersByTable = new HashMap<>();
    final Map<Long, Order> ordersById = new HashMap<>();
    final Map<Integer, TableLock> locks = new HashMap<>();
    final AtomicLong categorySeq = new AtomicLong(0);
    final AtomicLong productSeq = new AtomicLong(0);
    final AtomicLong orderSeq = new AtomicLong(1000);
    final AtomicLong lineSeq = new AtomicLong(5000);

    public FakeDataStore() {
        seedCatalog();
        seedTables();
    }

    void cleanupExpiredLocks() {
        Instant now = Instant.now();
        locks.entrySet().removeIf(e -> !e.getValue().isActive(now));
    }

    private void seedCatalog() {
        addCategory("Entrantes", "COCINA");
        addCategory("Bebidas", "BAR");
        addCategory("Pizzas", "COCINA");
        addCategory("Postres", "POSTRES");

        addProduct(1, "Bravas", 650, 1000, Destination.COCINA, "prod-dark");
        addProduct(1, "Ensalada", 900, 1000, Destination.COCINA, "prod-green");
        addProduct(1, "Calamares", 1200, 1000, Destination.COCINA, "prod-dark");
        addProduct(1, "Entrecot", 2000, 1000, Destination.COCINA, "prod-orange");

        addProduct(2, "Cerveza", 250, 2100, Destination.BAR, "prod-teal");
        addProduct(2, "Refresco", 300, 2100, Destination.BAR, "prod-orange");
        addProduct(2, "Copa Vino", 450, 2100, Destination.BAR, "prod-dark");
        addProduct(2, "Agua", 220, 1000, Destination.BAR, "prod-dark");

        addProduct(3, "Pizza Margarita", 1600, 1000, Destination.COCINA, "prod-dark");
        addProduct(3, "Cuatro Quesos", 1800, 1000, Destination.COCINA, "prod-dark");
        addProduct(3, "Pepperoni", 1750, 1000, Destination.COCINA, "prod-dark");

        addProduct(4, "Tarta Queso", 600, 1000, Destination.POSTRES, "prod-green");
        addProduct(4, "Brownie", 550, 1000, Destination.POSTRES, "prod-orange");
        addProduct(4, "Helado", 500, 1000, Destination.POSTRES, "prod-teal");
    }

    private Category addCategory(String name, String printDestination) {
        long id = categorySeq.incrementAndGet();
        Category category = new Category(id, name, normalizeDestination(printDestination));
        categories.add(category);
        return category;
    }

    synchronized Category createCategory(String name, String printDestination) {
        return addCategory(name, printDestination);
    }

    synchronized Category updateCategory(long id, String name, String printDestination) {
        for (int i = 0; i < categories.size(); i++) {
            Category current = categories.get(i);
            if (current.id() == id) {
                Category updated = new Category(id, name, normalizeDestination(printDestination));
                categories.set(i, updated);
                return updated;
            }
        }
        throw new IllegalArgumentException("Categoria no encontrada: " + id);
    }

    synchronized void deleteCategory(long id) {
        products.entrySet().removeIf(entry -> entry.getValue().categoryId() == id);
        categories.removeIf(c -> c.id() == id);
    }

    private static String normalizeDestination(String value) {
        if (value == null || value.isBlank()) {
            return "COCINA";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("BAR".equals(normalized) || "COCINA".equals(normalized) || "POSTRES".equals(normalized)) {
            return normalized;
        }
        return "COCINA";
    }

    private Product addProduct(long categoryId, String name, int price, int vatRateBps, Destination destination, String colorClass) {
        long id = productSeq.incrementAndGet();
        Product product = new Product(id, categoryId, name, price, vatRateBps, destination, colorClass);
        products.put(id, product);
        return product;
    }

    synchronized Product createProduct(long categoryId, String name, int price, int vatRateBps, Destination destination, String colorClass) {
        return addProduct(categoryId, name, price, vatRateBps, destination, colorClass);
    }

    synchronized Product updateProduct(long productId, long categoryId, String name, int price, int vatRateBps, Destination destination, String colorClass) {
        if (!products.containsKey(productId)) {
            throw new IllegalArgumentException("Producto no encontrado: " + productId);
        }
        Product product = new Product(productId, categoryId, name, price, vatRateBps, destination, colorClass);
        products.put(productId, product);
        return product;
    }

    synchronized void deleteProduct(long productId) {
        Product removed = products.remove(productId);
        if (removed == null) {
            throw new IllegalArgumentException("Producto no encontrado: " + productId);
        }
    }

    private void seedTables() {
        Order m2 = openOrderWithLines(2, 4, new String[]{"Pizza Margarita", "Cerveza"}, new int[]{1, 3});
        m2.getLines().get(0).setNote("SIN CEBOLLA");
        m2.getLines().forEach(OrderLine::markSentAll);

        Order m4 = openOrderWithLines(4, 2, new String[]{"Entrecot", "Cerveza"}, new int[]{1, 2});
        m4.getLines().forEach(OrderLine::markSentAll);
        m4.setBillRequested(true);

        openOrderWithLines(6, 5, new String[]{"Ensalada", "Refresco"}, new int[]{2, 2});

        Order m10 = openOrderWithLines(10, 3, new String[]{"Pizza Margarita", "Cerveza", "Bravas"}, new int[]{1, 2, 1});
        m10.getLines().get(0).markSentAll();

        Order m12 = openOrderWithLines(12, 4, new String[]{"Cuatro Quesos", "Cerveza", "Calamares"}, new int[]{1, 2, 1});
        m12.getLines().forEach(OrderLine::markSentAll);

        locks.put(4, new TableLock(4, "T-001", "admin", Instant.now().plusSeconds(85)));
        locks.put(10, new TableLock(10, "T-003", "terminal 3", Instant.now().plusSeconds(75)));
    }

    private Order openOrderWithLines(int tableId, int people, String[] names, int[] qtys) {
        long orderId = orderSeq.incrementAndGet();
        Order order = new Order(orderId, tableId, people, Instant.now().minusSeconds((tableId * 5L) * 60));
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Product p = products.values().stream().filter(prod -> prod.name().equalsIgnoreCase(name)).findFirst().orElseThrow();
            OrderLine line = new OrderLine(lineSeq.incrementAndGet(), p, qtys[i]);
            order.getLines().add(line);
        }
        openOrdersByTable.put(tableId, order);
        ordersById.put(order.getId(), order);
        return order;
    }
}

