package bthdg.calc;

import java.util.ArrayList;

public class RollingQueue<Type> extends ArrayList<Type> {
    private final int m_emaSize;
    private int m_tailIndex = 0;

    public RollingQueue(int emaSize) {
        super(emaSize);
        m_emaSize = emaSize;
    }

    public void enqueue(Type element) {
        set(m_tailIndex, element);
        m_tailIndex = (m_tailIndex + 1) % m_emaSize;
    }
}
