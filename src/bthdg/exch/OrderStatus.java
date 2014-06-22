package bthdg.exch;

public enum OrderStatus {
    NEW,
    SUBMITTED,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,
    CANCELLED,
    ERROR;

    public boolean isActive() {
        return (this == SUBMITTED) || (this == PARTIALLY_FILLED);
    }
}
