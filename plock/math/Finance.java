package plock.math;

// some basics: http://www.investopedia.com/articles/03/101503.asp
public class Finance {
    private double pv=0.0, fv=0.0, r=0.0, pmt=0.0, g=0.0, n=0.0;
    private boolean due = false;

    public enum SolveFor {pv, fv, r, n};

    public Finance pv(double pv) {this.pv=pv; return this;}
    public Finance fv(double fv) {this.fv=fv; return this;}
    public Finance pmt(double pmt) {this.pmt=pmt; return this;}
    public Finance r(double r) {this.r=r; return this;}
    public Finance g(double g) {this.g=g; return this;}
    public Finance n(double n) {this.n=n; return this;}
    public Finance due(boolean due) {this.due=due; return this;}

    public static final Finance create() {return new Finance();}

    public final double solve(String solveFor) {
        return solve(Enum.valueOf(SolveFor.class, solveFor));
    }
    public double solve(SolveFor solve) {
        switch (solve) {
            case fv:
                if (!due) {
                    return pv * Math.pow(1+r, n) + pmt/(r-g) * ( Math.pow(1+r,n) - Math.pow(1+g,n) );
                }
            case pv:
                if (!due) {
                    return fv/Math.pow(1+r,n) - pmt/(r-g) * (1 - Math.pow((1+g)/(1+r), n) );
                }
        }
        throw new IllegalArgumentException(solve+" not yet implemented");
    } 
    public static final double compoundInterestRate(double presentValue, double futureValue, double periods) {
        return Math.pow(futureValue/presentValue, 1/periods)-1;
    } 
    public static final double compoundInterestNumPeriods(double presentValue, double futureValue, double rate) {
        return Math.log(futureValue/presentValue)/Math.log(1+rate);
    }
}
