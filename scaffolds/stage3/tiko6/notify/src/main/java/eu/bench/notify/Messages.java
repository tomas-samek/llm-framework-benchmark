package eu.bench.notify;
public final class Messages {
    private Messages() {}
    public record ProductUpdate(String productId, String name, String category) {}
    public record UserUpdate(String userId, String name, String email) {}
    public record PriceUpdate(String productId, long priceCents, String currency) {}
    public record Purchase(String purchaseId, String userId, String productId, int quantity, String occurredAt) {}
    public record Notification(String purchaseId, String userId, String userName,
        String productId, String productName, Long priceCents, String currency,
        int quantity, Long totalCents, String occurredAt) {}
}
