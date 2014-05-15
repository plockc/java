package plock.math;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import static java.lang.Math.*;

// some basics: http://www.investopedia.com/articles/03/101503.asp
/** pmt, r, and g are all in terms of a single period, and g is per period and r is an effective rates
 *   such that any compounding is already included.  There are helper functions to convert
 *   between nominal to effective rates and vice versa, and there is a helper function to
 *   find the "effective payment" per period if payments and interest is compounded.  To compound
 *   payments and interest, configure everything in terms of a year, and simply set the amount of compounding.
 */
public class Finance implements Cloneable {
    private double pv=0.0, fv=0.0, r=0.0, pmt=0.0, g=0.0, n=0.0;
    /** this is all the payments together for a whole period with compounded interest on the payments */
    private double comp_pmt=0.0;
    /** this is the rate that will be compounded "comp" times across a single period */
    private double comp_r;
    private boolean due = false;
    private int comp = 1;

    public String toString() {return "{fv:"+fv+",pv:"+pv+",r:"+r+",comp_r:"+comp_r+",pmt:"+pmt
        +",comp_pmt:"+comp_pmt+",g:"+g+",n:"+n+",comp:"+comp+"}";}
    public enum TmvParams { pv, fv, r, g, n, pmt, comp_pmt, due, comp };

    public Finance pv(double pv) {this.pv=pv; return this;}
    public Finance fv(double fv) {this.fv=fv; return this;}
    /** TODO: check for compounding */
    public Finance r(double r) {
        this.r=r;
        if (comp == 1) {comp_r = r; return this;}
        comp_r = pow(1+r,1.0/comp)-1; // the monthly rate from the given effective annual rate
        return this; 
    }
    /** compounds the given nominal rate to determine effective rate */
    public Finance rFromNominal(double nominal) {r(pow(1+nominal/12,12)-1); return this;}
    public Finance comp_pmt(double payment) {
        comp_pmt = payment;
        if (comp == 1) {pmt=payment; return this;}
        if (comp_r == 0.0) {
            pmt = payment/comp;
        } else {
            pmt = comp_r*pv/(pow(1+comp_r,comp)-1);
        }
        return this;
    }
    public Finance pmt(double payment) {
        // TODO: annuity due
        pmt=payment;
        if (comp == 1) {comp_pmt=pmt; return this;}
        if (comp_r == 0.0) {
            comp_pmt=comp*pmt;
        } else {
            // this is an orinary annuity for "comp" months
            comp_pmt = payment/comp_r*(pow(1+comp_r,comp)-1);
        }
        return this;
    }
    public Finance g(double g) {this.g=g; return this;}
    public Finance n(double n) {this.n=n; return this;}
    public Finance comp(int comp) {
        this.comp = comp;
        r(r); pmt(pmt); // this will refigure out the compounded payment and rate
        return this;
    }
    public Finance due(boolean due) {this.due=due; return this;}

    public static final Finance create() {return new Finance();}

    public Map<String,?> getValues() {
        Stream s = Stream.of(TmvParams.pv, TmvParams.fv, TmvParams.pmt, TmvParams.r, 
                TmvParams.g, TmvParams.n, TmvParams.comp, TmvParams.comp_pmt);
        Collector c = Collectors.toMap(k->k.toString(),k->get(k.toString()));
        Map<String,?> vals = (Map<String,?>)s.collect(c);
        return vals;
    }

    public Double getDouble(String label) {return getDouble(TmvParams.valueOf(label)); }
    public Double getDouble(TmvParams p) {
        switch(p) {
            case pv: case fv: case r: case g: case n: case pmt: case comp_pmt:
             return (Double)get(p);
            default:
        }
        throw new IllegalArgumentException("cannot get "+p+" as a double");
    }    
    public Object get(String p) {return get(TmvParams.valueOf(p));}
    public Object get(TmvParams p) {
        switch (p) {
            case pv: return pv; 
            case fv: return fv;
            case r: return r;
            case g: return g;
            case pmt: return pmt;
            case comp_pmt: return comp_pmt;
            case n: return n;
            case comp: return comp;
            case due: return due;
        }
        throw new IllegalArgumentException("cannot get "+p);
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
            case comp_pmt: return comp_pmt(d);
        }
        throw new IllegalArgumentException("cannot set "+p+" with a double");
    }

    public Finance copy() {
        return new Finance().pv(pv).fv(fv).r(r).n(n).pmt(pmt).g(g).comp(comp).due(due);
    }

    public final double solve(String solveFor) {
        return solve(TmvParams.valueOf(solveFor));
    }
    /** Annuity formulas
        http://www.tvmcalcs.com/tvm/formulas/regular_annuity_formulas
        Annuity due calcs at 
        http://www.tvmcalcs.com/tvm/formulas/annuity_due_formulas
        A bunch of them
        http://www.calculatorsoup.com/calculators/financial/
    */
    public double solve(TmvParams solve) {
        if (due) { return copy().pv(pv+pmt).due(false).solve(solve); }
        double rateFactor = pow(1+r,n);
        switch (solve) {
            case fv:
                if (g==r) {
                    return comp_pmt*n*pow(1+r,n-1);
                } else {
                    return pv * rateFactor + comp_pmt/(r-g) * ( rateFactor - pow(1+g,n) );
                }
            case pv:
                /* this doesn't work with the compounding
                if (r==g) { return copy().r(r+=.0000001).solve(TmvParams.pv); } 
                return fv/rateFactor - pmt/(r-g) * (1 - pow((1+g)/(1+r), n) );
                */
                break;
            case pmt:
                /* this does not work with the compounding
                if (g == 0) {
                    return r*(fv-pv*rateFactor)/(rateFactor-1);
                }
                */
                break;
            case r: 
                /* this does not work with the compounding 
                if (g == 0) {
                    return pow( (fv+pmt/r) / (pmt/r+pv), 1/n) -1;
                }
                */
                break;
            case g:
                if (pmt==0.0 || n == 0.0) {throw new IllegalArgumentException("cannot solve g with no payments");}
                break;
            case n:
                break;
            case comp_pmt:
                break;
            default:
                throw new IllegalArgumentException("not implemented for "+solve);
        }
        return Math.iterateSolve(x->{return copy().set(solve,x).solve(TmvParams.fv);},fv,0.0,.00000001,.000001);
    } 
    public static final double compoundInterestRate(double presentValue, double futureValue, double periods) {
        return pow(futureValue/presentValue, 1/periods)-1;
    } 
    public static final double compoundInterestNumPeriods(double presentValue, double futureValue, double rate) {
        return log(futureValue/presentValue)/log(1+rate);
    }
    public static void main(String[] args) {
        System.out.println("simple example: "+growingAnnuityWithCompoundPayments(1.03923, 2, 2, 1.02, 200));
        System.out.println("simple example: "+growingAnnuityWithCompoundPayments(1.006434, 12, 14, 1.02, 200));
    }
    /** @param r monthly rate
     *  @param y months in year (12)
     *  @param a years-1
     *  @param b growth rate per year
     *  @param z monthly deposit amount
     */
    private static double growingAnnuityDueWithCompoundPayments(double r, double y, double a, double b, double z) {
        return ( r * (-1+pow(r,y)) * (pow(-b, a+1)+pow(r,(1+a)*y)) * z ) / ( (-1+r) * (-b+pow(r,y)) );
    }
    private static double growingAnnuityWithCompoundPayments(double r, double y, double a, double b, double z) {
        return ((-1 + pow(r,y))*(pow(-b,(1 + a)) + pow(r,(1 + a)*y))*z)/((-1 + r)*(-b + pow(r,y)));
    }
}
