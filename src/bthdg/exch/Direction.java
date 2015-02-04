package bthdg.exch;

public enum Direction {
    FORWARD(true) {
        @Override public Direction reverse() { return BACKWARD; }
    },
    BACKWARD(false) {
        @Override public Direction reverse() { return FORWARD; }
        @Override public double applyDirection(double value) {return -value;}
        @Override public OrderSide orderSide() { return OrderSide.SELL; }
    };

    public boolean m_forward;

    Direction(boolean forward) {
        m_forward = forward;
    }
    public Direction reverse() { return null; }
    public double applyDirection(double value) {return value;}
    public OrderSide orderSide() { return OrderSide.BUY; }

    public static Direction get(double diff) {
        return (diff > 0) ? FORWARD : BACKWARD;
    }
}
