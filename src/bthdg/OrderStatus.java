package bthdg;

public enum OrderStatus {
    NEW,
    SUBMITTED,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,
    CANCELLED;

    public boolean isActive() {
        return (this == SUBMITTED) || (this == PARTIALLY_FILLED);
    }
}
