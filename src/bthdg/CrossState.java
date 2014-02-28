package bthdg;

public enum CrossState {
    NONE,
    OPEN_BRACKETS_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
            log("CrossState.checkState() " + this);
            if(crossData.checkOpenBracketsExecuted(iContext, forkData) ) {
                crossData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    ONE_OPEN_BRACKETS_EXECUTED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
            log("CrossState.checkState() " + this);
            crossData.checkOpenBracketsExecuted(iContext, forkData);
        }
    },
    BOTH_OPEN_BRACKETS_EXECUTED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) {
            log("CrossState.checkState() " + this);
        }
    },
    MKT_BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) {
            log("CrossState.checkState() " + this);
        }
    },
    ERROR;

    public void checkState(IterationContext iContext, ForkData forkData, CrossData crossData) throws Exception {
        log("checkState not implemented for " + this);
    }

    private static void log(String s) { Log.log(s); }
}
