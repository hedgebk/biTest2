package bthdg.triplet;

public enum Direction {
    FORWARD(true) {
        @Override public Direction reverse() { return BACKWARD; }
    },
    BACKWARD(false) {
        @Override public Direction reverse() { return FORWARD; }
    };

    public boolean m_forward;

    Direction(boolean forward) {
        m_forward = forward;
    }

    public Direction reverse() { return null; }
}
