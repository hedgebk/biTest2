package bthdg;

public enum CrossState {
    NONE,
    BRACKETS_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
            log("CrossState.checkState() " + this);
            if(crossData.checkBracketsExecuted(iContext, forkData) ) {
                crossData.moveBracketsIfNeeded(iContext, forkData);
            }
        }
    },
    ONE_BRACKET_EXECUTED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
            log("CrossState.checkState() " + this);
            crossData.checkBracketsExecuted(iContext, forkData);
        }
    },
    MKT_BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
            log("CrossState.checkState() " + this);
            crossData.checkMarketBracketsExecuted(iContext, forkData);
        }
    },
    BOTH_BRACKETS_EXECUTED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) {
            log("CrossState.checkState() " + this);
        }
    },
    STOP { // something not OK to continue execution
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) {
            log("CrossState.checkState() " + this);
        }
    },
    ERROR,
    ;

    public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
        log("checkState not implemented for " + this);
    }

    private static void log(String s) { Log.log(s); }
}
