package bthdg.duplet;

public enum ForkDirection {
    DOWN { // 1>2
        @Override public SharedExchangeData buyExch(PairExchangeData pairExData) { return pairExData.m_sharedExch2; }
        @Override public SharedExchangeData sellExch(PairExchangeData pairExData) { return pairExData.m_sharedExch1; }
        @Override public ForkDirection opposite() { return UP; }
        @Override public double apply(double v) { return -v; }
    },
    UP {   // 1<2
        @Override public SharedExchangeData buyExch(PairExchangeData pairExData) { return pairExData.m_sharedExch1; }
        @Override public SharedExchangeData sellExch(PairExchangeData pairExData) { return pairExData.m_sharedExch2; }
        @Override public ForkDirection opposite() { return DOWN; }
        @Override public double apply(double v) { return v; }
    };

    public SharedExchangeData buyExch(PairExchangeData pairExData) { return null; }
    public SharedExchangeData sellExch(PairExchangeData pairExData) { return null; }
    public ForkDirection opposite() { return null; }
    public double apply(double v) { return 0; }
}
