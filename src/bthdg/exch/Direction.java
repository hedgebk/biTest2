package bthdg.exch;

public enum Direction {
    FORWARD(true) {
        @Override public Direction reverse() { return BACKWARD; }
        @Override public boolean isForward() {
            return true;
        }
    },
    BACKWARD(false) {
        @Override public Direction reverse() { return FORWARD; }
        @Override public double applyDirection(double value) {return -value;}
        @Override public OrderSide orderSide() { return OrderSide.SELL; }
        @Override public boolean isBackward() { return true; }
    };

    public boolean m_forward;

    Direction(boolean forward) {
        m_forward = forward;
    }
    public Direction reverse() { return null; }
    public double applyDirection(double value) {return value;}
    public OrderSide orderSide() { return OrderSide.BUY; }
    public boolean isForward() { return false; }
    public boolean isBackward() { return false; }

    public static Direction get(double diff) {
        return (diff > 0) ? FORWARD : BACKWARD;
    }
    public static Direction get(boolean up) { return up ? FORWARD : BACKWARD; }

    public static boolean isForward(Direction oscDirection) {
        return (oscDirection != null) && oscDirection.isForward();
    }

    public static boolean isBackward(Direction oscDirection) {
        return (oscDirection != null) && oscDirection.isBackward();
    }
}
