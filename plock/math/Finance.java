package plock.math;

import java.util.*;
import java.util.stream.*;

// some basics: http://www.investopedia.com/articles/03/101503.asp
public class Finance {
    private double pv=0.0, fv=0.0, r=0.0, pmt=0.0, g=0.0, n=0.0;
    private boolean due = false;

    public String toString() {return "{fv:"+fv+",pv:"+pv+",r:"+r+",pmt:"+pmt+",g:"+g+",n:"+n+"}";}
    public enum TmvParams { pv, fv, r, g, n, pmt, due };

    public Finance pv(double pv) {this.pv=pv; return this;}
    public Finance fv(double fv) {this.fv=fv; return this;}
    public Finance pmt(double pmt) {this.pmt=pmt; return this;}
    public Finance r(double r) {this.r=r; return this;}
    public Finance g(double g) {this.g=g; return this;}
    public Finance n(double n) {this.n=n; return this;}
    public Finance due(boolean due) {this.due=due; return this;}

    public static final Finance create() {return new Finance();}

    public Map<String,Object> getValues() {
        Stream s = Stream.of(TmvParams.pv, TmvParams.fv, TmvParams.pmt, TmvParams.r, TmvParams.g, TmvParams.n);
        Collector c = Collectors.toMap(k->k.toString(),k->getDouble(k.toString()));
        Map<String,Object> vals = (Map<String,Object>)s.collect(c);
        return vals;
    }

    public Double getDouble(String label) {return getDouble(TmvParams.valueOf(label)); }
    public Double getDouble(TmvParams p) {
        switch (p) {
            case pv: return pv; 
            case fv: return fv;
            case r: return r;
            case g: return g;
            case n: return n;
            case pmt: return pmt;
        }
        throw new IllegalArgumentException("cannot set "+p+" with a double");
    }
    public Finance set(String label, double d) {return set(TmvParams.valueOf(label), d); }
    public Finance set(TmvParams p, double d) {
        switch (p) {
            case pv: return pv(d); 
            case fv: return fv(d);
            case r: return r(d);
            case g: return g(d);
            case n: return n(d);
            case pmt: return pmt(d);
        }
        throw new IllegalArgumentException("cannot set "+p+" with a double");
    }

    public final double solve(String solveFor) {
        return solve(TmvParams.valueOf(solveFor));
    }
    public double solve(TmvParams solve) {
        switch (solve) {
            case fv:
                if (!due) {
                    double r = this.r;
                    if (r-g==0) {r+=.0000001;} 
                    return pv * Math.pow(1+r, n) + pmt/(r-g) * ( Math.pow(1+r,n) - Math.pow(1+g,n) );
                }
            case pv:
                if (!due) {
                    double r = this.r;
                    if (r-g==0) {r+=.0000001;}
                    return fv/Math.pow(1+r,n) - pmt/(r-g) * (1 - Math.pow((1+g)/(1+r), n) );
                }
            case pmt:
                if (!due && g == 0) {
                    return r*(fv-pv*Math.pow(1+r,n))/(Math.pow(1+r,n)-1);
                }
            case r:
                if (g == 0 && !due) {
                    return Math.pow( (fv+pmt/r) / (pmt/r+pv), 1.0/n) -1;
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
