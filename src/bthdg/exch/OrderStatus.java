package bthdg.exch;

public enum OrderStatus {
    NEW,
    SUBMITTED,
    PARTIALLY_FILLED {
        @Override public boolean partialOrFilled() { return true; }
    },
    FILLED {
        @Override public boolean partialOrFilled() { return true; }
    },
    REJECTED,
    CANCELLED,
    ERROR;

    /** submitted but not yet fully executed: SUBMITTED or PARTIALLY_FILLED */
    public boolean isActive() {
        return (this == SUBMITTED) || (this == PARTIALLY_FILLED);
    }

    public boolean partialOrFilled() { return false; }
}
