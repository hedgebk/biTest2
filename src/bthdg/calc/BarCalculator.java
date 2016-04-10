package bthdg.calc;

import bthdg.Log;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;

public abstract class BarCalculator {
    private static boolean LOG_JUMP = false;

    private final long m_barSizeMillis;
    private final long m_barsMillisOffset;
    protected long m_currentBarStart;
    protected long m_currentBarEnd;
    private long m_pendingTickTime;
    private ArrayList<Double> m_pendings;
    public long m_lastTickTime;
    public double m_lastTickPrice;

    private static void log(String s) { Log.log(s); }

    BarCalculator(long barSizeMillis, long barsMillisOffset) {
        if (barSizeMillis == 0) {
            throw new RuntimeException("barSizeMillis==0");
        }
        m_barSizeMillis = barSizeMillis;
        m_barsMillisOffset = barsMillisOffset;
    }

    /** @return true if current bar changed */
    public boolean update(long time, double price) {
        return update(time, price, true);
    }

    /** @return true if current bar changed */
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
                if (LOG_JUMP) {
                    log("  not confirmed pendings for time=" + m_pendingTickTime + " got time=" + time + " in current bar; currentBarEnd=" + m_currentBarEnd + "; pendings=" + m_pendings);
                }
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
            if (LOG_JUMP) {
                log("got bar jump. diff=" + diff);
            }

            if (time < m_pendingTickTime) {
                if (LOG_JUMP) {
                    log(" still bar jump, but closer. updating for diff=" + diff + "; forgetting pending ticks: " + m_pendings);
                }
                if (m_pendings != null) {
                    m_pendings.clear();
                }
                m_pendingTickTime = 0;
            }

            if (m_pendingTickTime == 0) { // first jump tick
                addToPendings(time, price, true);
                if (LOG_JUMP) {
                    log("  tick added to pending queue. pendingTickTime=" + m_pendingTickTime + "; queue.size=" + m_pendings.size() + "; queue=" + m_pendings);
                }
            } else if (m_pendingTickTime == time) { // jump tick at the same time as previous
                addToPendings(time, price, false);
                if (LOG_JUMP) {
                    log("  tick added to pending queue. pendingTickTime=" + m_pendingTickTime + "; queue.size=" + m_pendings.size() + "; queue=" + m_pendings);
                }
            } else if (m_pendingTickTime < time) { // got tick after pending time. is this confirmation ?
                if (LOG_JUMP) {
                    log(" got tick after pending time. is this confirmation? pendingTickTime=" + m_pendingTickTime + "; time=" + time);
                }
                long pendingBarStart = getBarStart(m_pendingTickTime);
                long tickBarStart = getBarStart(time);
                if (pendingBarStart == tickBarStart) {
                    long dif2 = time - m_pendingTickTime;
                    if (LOG_JUMP) {
                        log("  tick and pending are from the same bar (dif2=" + dif2 + ") - confirming ticks: " + m_pendings);
                    }
                    ArrayList<Double> pendings = new ArrayList<Double>(m_pendings);
                    try {
                        m_pendings.clear();
                        m_pendingTickTime = 0;

                        for (Double pending : pendings) {
                            update(m_pendingTickTime, pending, false);
                        }
                    } catch (ConcurrentModificationException cme) {
                        log("GOT ConcurrentModificationException. pendings=" + pendings);
                        throw cme;
                    }
                    update(time, price, false);
                    return true;
                }
                if (LOG_JUMP) {
                    log("  tick is from older bar than pending. forgetting pending ticks. " + m_pendings);
                }
                addToPendings(time, price, true);
                if (LOG_JUMP) {
                    log("   tick added to pending queue. pendingTickTime=" + m_pendingTickTime + "; queue.size=" + m_pendings);
                }
            }
            return false;
        }
        if (m_pendingTickTime != 0) {
            if (LOG_JUMP) {
                log("  not confirmed pendings for time=" + m_pendingTickTime + ": " + m_pendings);
            }
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

    private long getBarStart(long time) {
        return (time - m_barsMillisOffset) / m_barSizeMillis * m_barSizeMillis + m_barsMillisOffset;
    }

    protected abstract void startNewBar(long barStart, long barEnd);
    public abstract boolean updateCurrentBar(long time, double price);
    protected abstract void finishCurrentBar(long time, double price);

    static void replaceLastElement(LinkedList<Double> list, double price) {
        if (!list.isEmpty()) {
            list.set(list.size() - 1, price); // replace last element
        }
    }
}
