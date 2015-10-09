package bthdg.calc;

import bthdg.Log;

import java.util.ArrayList;
import java.util.LinkedList;

public abstract class BarCalculator {
    private final long m_barSizeMillis;
    private final long m_barsMillisOffset;
    protected long m_currentBarStart;
    protected long m_currentBarEnd;
    private long m_pendingTickTime;
    private ArrayList<Double> m_pendings;
    public long m_lastTickTime;
    public double m_lastTickPrice;

    private static void log(String s) { Log.log(s); }

    public BarCalculator(long barSizeMillis, long barsMillisOffset) {
        if (barSizeMillis == 0) {
            throw new RuntimeException("barSizeMillis==0");
        }
        m_barSizeMillis = barSizeMillis;
        m_barsMillisOffset = barsMillisOffset;
    }

    public boolean update(long time, double price) {
        return update(time, price, true);
    }

    private boolean update(long time, double price, boolean checkJump) {
        if (m_currentBarEnd < time) { // the tick is after current bar ?
            if (m_currentBarStart != 0) {
                if (checkJump) {
                    Boolean changed = checkJump(time, price);
                    if (changed != null) {
                        return changed;
                    }
                }
                finishCurrentBar(time, price);
            }
            m_currentBarStart = getBarStart(time);
            m_currentBarEnd = m_currentBarStart + m_barSizeMillis;
            startNewBar(m_currentBarStart, m_currentBarEnd);
        } else {
            if (m_pendingTickTime != 0) {
                log("  not confirmed pendings for time=" + m_pendingTickTime + " got time=" + time + " in current bar; currentBarEnd=" + m_currentBarEnd + "; pendings=" + m_pendings);
                if (m_pendings != null) {
                    m_pendings.clear();
                }
                m_pendingTickTime = 0;
            }
        }
        boolean updated = updateCurrentBar(time, price);
        setLastTickTimePrice(time, price);
        return updated;
    }

    protected void setLastTickTimePrice(long time, double price) {
        m_lastTickTime = time;
        m_lastTickPrice = price;
    }

    private Boolean checkJump(long time, double price) {
        long diff = time - m_currentBarEnd;
        if (diff > m_barSizeMillis) {
            // bar jump. some bar skipped - need to confirm tick by other tick from the same bar ...
            log("got bar jump. diff=" + diff);

            if (time < m_pendingTickTime) {
                log(" still bar jump, but closer. updating for diff=" + diff + "; forgetting pending ticks: " + m_pendings);
                if (m_pendings != null) {
                    m_pendings.clear();
                }
                m_pendingTickTime = 0;
            }

            if (m_pendingTickTime == 0) { // first jump tick
                addToPendings(time, price, true);
                log("  tick added to pending queue. pendingTickTime=" + m_pendingTickTime + "; queue.size=" + m_pendings.size() + "; queue=" + m_pendings);
            } else if (m_pendingTickTime == time) { // jump tick at the same time as previous
                addToPendings(time, price, false);
                log("  tick added to pending queue. pendingTickTime=" + m_pendingTickTime + "; queue.size=" + m_pendings.size() + "; queue=" + m_pendings);
            } else if (m_pendingTickTime < time) { // got tick after pending time. is this confirmation ?
                log(" got tick after pending time. is this confirmation? pendingTickTime=" + m_pendingTickTime + "; time=" + time);
                long pendingBarStart = getBarStart(m_pendingTickTime);
                long tickBarStart = getBarStart(time);
                if (pendingBarStart == tickBarStart) {
                    long dif2 = time - m_pendingTickTime;
                    log("  tick and pending are from the same bar (dif2=" + dif2 + ") - confirming ticks: " + m_pendings);
                    for (double pending : m_pendings) {
                        update(m_pendingTickTime, pending, false);
                    }
                    m_pendings.clear();
                    m_pendingTickTime = 0;
                    update(time, price, false);
                    return true;
                }
                log("  tick is from older bar than pending. forgetting pending ticks. " + m_pendings);
                addToPendings(time, price, true);
                log("   tick added to pending queue. pendingTickTime=" + m_pendingTickTime + "; queue.size=" + m_pendings);
            }
            return false;
        }
        if (m_pendingTickTime != 0) {
            log("  not confirmed pendings for time=" + m_pendingTickTime + ": " + m_pendings);
            if (m_pendings != null) {
                m_pendings.clear();
            }
            m_pendingTickTime = 0;
        }
        return null;
    }

    private void addToPendings(long time, double price, boolean clear) {
        m_pendingTickTime = time;
        if (m_pendings != null) {
            if (clear) {
                m_pendings.clear();
            }
        } else {
            m_pendings = new ArrayList<Double>();
        }
        m_pendings.add(price);
    }

    protected long getBarStart(long time) {
        return (time - m_barsMillisOffset) / m_barSizeMillis * m_barSizeMillis + m_barsMillisOffset;
    }

    protected abstract void startNewBar(long barStart, long barEnd);
    protected abstract boolean updateCurrentBar(long time, double price);
    protected abstract void finishCurrentBar(long time, double price);

    protected static void replaceLastElement(LinkedList<Double> list, double price) {
        if (!list.isEmpty()) {
            list.set(list.size() - 1, price); // replace last element
        }
    }
}
