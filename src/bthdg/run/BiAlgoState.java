package bthdg.run;

import bthdg.exch.OrderData;
import bthdg.exch.OrderStatus;

import java.util.Arrays;

public enum BiAlgoState {
    NEW,
    OPEN_PLACED {
        @Override public boolean isPending() { return true; }
        @Override public Boolean isOpen() { return true; }
        @Override public void checkState(BiAlgoData biAlgoData) { checkPlacedState(biAlgoData, OPEN, SOME_OPEN, true); }
    },
    SOME_OPEN {
        @Override public boolean isPending() { return true; }
        @Override public Boolean isOpen() { return true; }
        @Override public void checkState(BiAlgoData biAlgoData) {
            checkSomeDone(biAlgoData, OPEN, true);
        }
    },
    OPEN,
    CLOSE_PLACED {
        @Override public boolean isPending() { return true; }
        @Override public Boolean isOpen() { return false; }
        @Override public void checkState(BiAlgoData biAlgoData) { checkPlacedState(biAlgoData, CLOSE, SOME_CLOSE, false); }
    },
    SOME_CLOSE {
        @Override public boolean isPending() { return true; }
        @Override public Boolean isOpen() { return false; }
        @Override public void checkState(BiAlgoData biAlgoData) { checkSomeDone(biAlgoData, CLOSE, false); }
    },
    CLOSE,
    CANCELING {
        @Override public void checkState(BiAlgoData biAlgoData) { checkCancelling(biAlgoData); }
    },
    CANCEL,
    ERROR {
        @Override public void checkState(BiAlgoData biAlgoData) { /*noop*/ }
    };

    private static void checkCancelling(BiAlgoData biAlgoData) {
        if( checkAllDone(biAlgoData) ) {
            biAlgoData.log(" no more active orders - setting CANCELLED");
            biAlgoData.setState(CANCEL);
        }
    }

    private static boolean checkAllDone(BiAlgoData biAlgoData) {
        OrderDataExchange[] odes = biAlgoData.getOrderDataExchanges();
        biAlgoData.log("checkAllDone() odes=" + Arrays.asList(odes));
        boolean hasActive = false;
        for(OrderDataExchange ode: odes) {
            if(ode != null) {
                if( ode.m_orderData.m_status.isActive() ) {
                    biAlgoData.log(" got active order=" + ode);
                    hasActive = true;
                    break;
                }
            }
        }
        return !hasActive;
    }

    private static void checkSomeDone(BiAlgoData biAlgoData, BiAlgoState filledState, boolean opening) {
        OrderDataExchange ode1 = opening ? biAlgoData.m_openOde1 : biAlgoData.m_closeOde1;
        OrderDataExchange ode2 = opening ? biAlgoData.m_openOde2 : biAlgoData.m_closeOde2;
        OrderData od1 = ode1.m_orderData;
        OrderData od2 = ode2.m_orderData;
        OrderStatus status1 = od1.m_status;
        OrderStatus status2 = od2.m_status;
        String side = (opening ? "open" : "close");
        if ((status1 == OrderStatus.FILLED) && (status2 == OrderStatus.FILLED)) { // both filled
            double filled1 = od1.m_filled;
            double filled2 = od2.m_filled;
            double done = Math.min(filled1, filled2);
            biAlgoData.log("both executed: filled1=" + filled1 + "; filled2=" + filled2 + ";  " +
                    side + "=" + done);
            if (opening) {
                biAlgoData.m_open = done;
            } else {
                biAlgoData.m_close = done;
            }
            biAlgoData.setState(filledState);
        } else if (status1.partialOrFilled() || status2.partialOrFilled()) {
            double filled1 = od1.m_filled;
            double filled2 = od2.m_filled;
            double newDone = Math.min(filled1, filled2);
            double oldDone = biAlgoData.m_open;
            if (newDone > oldDone) {
                biAlgoData.log("some more executed: " +
                        "old " + side + "=" + oldDone +
                        "; new " + side + "=" + newDone +
                        "; filled1=" + filled1 + "; filled2=" + filled2);
                if (opening) {
                    biAlgoData.m_open = newDone;
                } else {
                    biAlgoData.m_close = newDone;
                }
            } else {
                biAlgoData.log("no more executed: filled1=" + filled1 + "; filled2=" + filled2);
            }
        } else {
            biAlgoData.log("ERROR: NOT supported state: status1=" + status1 + ", status2=" + status2);
        }
    }

    private static void checkPlacedState(BiAlgoData biAlgoData, BiAlgoState filledState, BiAlgoState partialState, boolean opening) {
        OrderDataExchange ode1 = opening ? biAlgoData.m_openOde1 : biAlgoData.m_closeOde1;
        OrderDataExchange ode2 = opening ? biAlgoData.m_openOde2 : biAlgoData.m_closeOde2;
        OrderData od1 = ode1.m_orderData;
        OrderData od2 = ode2.m_orderData;
        OrderStatus status1 = od1.m_status;
        OrderStatus status2 = od2.m_status;
        if ((status1 == OrderStatus.FILLED) && (status2 == OrderStatus.FILLED)) { // both filled
            double filled1 = od1.m_filled;
            double filled2 = od2.m_filled;
            double done = Math.min(filled1, filled2);
            if (opening) {
                biAlgoData.m_open = done;
            } else {
                biAlgoData.m_close = done;
            }
            biAlgoData.log("both executed: filled1=" + filled1 + "; filled2=" + filled2 + ";  " +
                    (opening ? "open" : "close") + "=" + done);
            biAlgoData.setState(filledState);
        } else if (status1.partialOrFilled() || status2.partialOrFilled()) {
            double filled1 = od1.m_filled;
            double filled2 = od2.m_filled;
            double done = Math.min(filled1, filled2);
            if (opening) {
                biAlgoData.m_open = done;
            } else {
                biAlgoData.m_close = done;
            }
            biAlgoData.log("some executed: filled1=" + filled1 + "; filled2=" + filled2 + ";  " +
                    (opening ? "open" : "close") + "=" + done);
            biAlgoData.setState(partialState);
        } else if ((status1 == OrderStatus.SUBMITTED) && (status2 == OrderStatus.SUBMITTED)) {
            biAlgoData.log("nothing yet executed");
        } else {
            biAlgoData.log("on supported state: status1=" + status1 + ", status2=" + status2);
        }
    }

    public boolean isPending() { return false; }
    public void checkState(BiAlgoData biAlgoData) { throw new RuntimeException("checkState() not implemented yet for " + this); }
    public Boolean isOpen() { return null; }
}
