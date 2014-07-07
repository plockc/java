package plock.math;

import java.util.*;
import java.util.stream.*;

import javafx.beans.value.*;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.binding.*;
import static java.lang.Math.*;
import plock.prop.DoublePropertyFunctionWrapper;

// some basics: http://www.investopedia.com/articles/03/101503.asp
/** pmt, r, and g are all in terms of a single period, and g is per period and r is an effective rates
 *   such that any compounding is already included.  There are helper functions to convert
 *   between nominal to effective rates and vice versa, and there is a helper function to
 *   find the "effective payment" per period if payments and interest is compounded.  To compound
 *   payments and interest, configure everything in terms of a year, and simply set the amount of compounding.
 */
public class Finance implements Cloneable {
    private TmvParams solveFor = TmvParams.fv;
    private final DoubleBinding computedSolution = new DoubleBinding() {
        @Override protected double computeValue() {/*System.out.println("got "+solve());*/ return solve();}
    };
    private final SimpleDoubleProperty fv = new SimpleDoubleProperty();
    private final SimpleDoubleProperty pv = new SimpleDoubleProperty();
    private final SimpleDoubleProperty r = new SimpleDoubleProperty();
    private final SimpleDoubleProperty pmt = new SimpleDoubleProperty();
    private final SimpleDoubleProperty g = new SimpleDoubleProperty();
    private final SimpleDoubleProperty n = new SimpleDoubleProperty();
    private final SimpleIntegerProperty comp = new SimpleIntegerProperty(1);
    private final SimpleBooleanProperty due = new SimpleBooleanProperty();
    /** this is the rate that will be compounded "comp" times across a single period */
    private final DoubleBinding comp_r = new DoubleBinding() {
        {Stream.of((ObservableValue)r,comp).forEach(obs->obs.addListener((obj,oldVal,newVal)->invalidate()));}
        @Override protected double computeValue() {
            //System.out.println("calc comp_r from r:"+r.get()+" and comp:" +comp.get());
            if (comp.get() == 1) {return r.get();}
            return pow(1+r.get(),1.0/comp.get())-1; // the monthly rate from the given effective annual rate
        }
    };
    /** this is all the payments together for a whole period with compounded interest on the payments */
    private final DoubleProperty comp_pmt = DoublePropertyFunctionWrapper.bidirectionalBind(pmt,
        payment -> {
            //System.out.println("comp_pmt is recalculated based on pmt "+payment+" with comp: "+comp.get());
            if (comp.get() == 1) {return payment;}
            if (comp_r.get() == 0.0) {
                return comp.get()*payment;
            }
            // this is an orinary annuity for "comp" months
            return payment/comp_r.get() * (pow(1+comp_r.get(),comp.get())-1);
        },
        inv-> {
            //System.out.println("pmt is getting recalculated based on comp_pmt "+inv+" with comp: "+comp.get());
            if (comp.get() == 1) {return inv;}
            if (comp_r.get() == 0.0) {
                return inv/comp.get();
            }
            return inv*comp_r.get() / (pow(1+comp_r.get(),comp.get())-1);
        }, new SimpleDoubleProperty(), comp, r); // when comp updates, need to recalc

    public Finance() {
        fv.bind(computedSolution);
        configureSolutionInvalidation();
        //System.out.println("created without map: "+getValues());
    }
    public Finance(Map<String,Object> init, TmvParams solveFor) {
        Stream.of(TmvParams.pv,TmvParams.r,TmvParams.g,TmvParams.n,TmvParams.comp_pmt,TmvParams.pmt,TmvParams.comp)
            .filter(p->!p.equals(solveFor)) .forEach(p->{
                //System.out.println("setting "+p+" to "+init.get(p.toString()));
                set(p.toString(), (Number)init.get(p.toString())); });
        if (solveFor != TmvParams.fv) {
            set("fv", (Double)init.get("fv"));
        	this.solveFor = solveFor;
        }
        getProperty(solveFor).bind(computedSolution);
        configureSolutionInvalidation();
        computedSolution.invalidate();
        //System.out.println("Created with map: "+this.getValues()+" from: "+init);
    }

    private ChangeListener<?> solutionInvalidationListener = (bean,oldVal,newVal) -> {
    	computedSolution.invalidate();
    };
    @SuppressWarnings("unchecked")
	private void configureSolutionInvalidation() {
    	Stream.of(TmvParams.pv, TmvParams.fv, TmvParams.pmt, TmvParams.r, 
                TmvParams.g, TmvParams.n, TmvParams.comp).filter(p->!solveFor.equals(p))
                    .forEach(p->getProperty(p).addListener(solutionInvalidationListener));
    }

    public String toString() {return "{fv:"+fv+",pv:"+pv+",r:"+r+",comp_r:"+comp_r+",pmt:"+pmt
        +",comp_pmt:"+comp_pmt+",g:"+g+",n:"+n+",comp:"+comp+"}";}
    public enum TmvParams { pv, fv, r, g, n, pmt, comp_pmt, due, comp };

    public Finance pv(double pv) {this.pv.set(pv); return this;}
    public Finance fv(double fv) {this.fv.set(fv); return this;}
    /** TODO: check for compounding */
    public Finance r(double r) { this.r.set(r); return this;}
    /** compounds the given nominal rate to determine effective rate */
    public Finance rFromNominal(double nominal) {r(pow(1+nominal/12,12)-1); return this;}
    public Finance comp_pmt(double payment) {this.comp_pmt.set(payment); return this;}
    public Finance pmt(double payment) {this.pmt.set(payment); return this;}
    public Finance g(double g) {this.g.set(g); return this;}
    public Finance n(double n) {this.n.set(n); return this;}
    public Finance comp(int comp) { this.comp.set(comp); return this; }
    public Finance due(boolean due) {this.due.set(due); return this;}

    public static final Finance create() {return new Finance();}

    public Map<String,Object> getValues() {
        Stream s = Stream.of(TmvParams.pv, TmvParams.fv, TmvParams.pmt, TmvParams.r, 
                TmvParams.g, TmvParams.n, TmvParams.comp, TmvParams.comp_pmt);
        Collector c = Collectors.toMap(k->k.toString(),k->get(k.toString()));
        Map<String,Object> vals = (Map<String,Object>)s.collect(c);
        vals.put("comp_r", comp_r.getValue());
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
    public Property getProperty(String p) {return getProperty(TmvParams.valueOf(p));}
    public Property getProperty(TmvParams p) {
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
    public Object get(TmvParams p) { /*System.out.println("Getting "+p+" "+getProperty(p)); */return getProperty(p).getValue(); }
    public double getSolution() {return (Double)getProperty(solveFor).getValue();}
    public Finance set(String label, Number d) {return set(TmvParams.valueOf(label), d); }
    public Finance set(TmvParams p, Number d) {
        switch (p) {
            case pv: return pv(d.doubleValue()); 
            case fv: return fv(d.doubleValue());
            case r: return r(d.doubleValue());
            case g: return g(d.doubleValue());
            case n: return n(d.doubleValue());
            case comp: return comp(d.intValue());
            case pmt: return pmt(d.doubleValue());
            case comp_pmt: return comp_pmt(d.doubleValue());
        }
        throw new IllegalArgumentException("cannot set "+p+" with a double");
    }

    public Finance copy() {
        return new Finance(getValues(), solveFor);
    }

    public TmvParams getSolveFor() {return solveFor;}
    public final Finance solveFor(String solveFor) {
        return solveFor(TmvParams.valueOf(solveFor));
    }
    public Finance solveFor(TmvParams solveFor) {
    	TmvParams oldSolveFor = this.solveFor;
    	getProperty(oldSolveFor).unbind(); // this unbinds the solve() method from the old solution property
    	// we want to invalidate solution when the old solveFor changes now, and don't invalidate when solution changes
    	getProperty(solveFor).removeListener(solutionInvalidationListener);
    	getProperty(oldSolveFor).addListener(solutionInvalidationListener);

    	this.solveFor = solveFor;
        getProperty(solveFor).bind(computedSolution);
        computedSolution.invalidate();
        return this;
    }
    /** Annuity formulas
        http://www.tvmcalcs.com/tvm/formulas/regular_annuity_formulas
        Annuity due calcs at 
        http://www.tvmcalcs.com/tvm/formulas/annuity_due_formulas
        A bunch of them
        http://www.calculatorsoup.com/calculators/financial/
    */
    private double solve() {
        if (due.get()) { return (Double)copy().pv(pv.get()+pmt.get()).due(false).getSolution(); }
        switch (solveFor) {
            case fv:
                double rateFactor = pow(1+r.get(),n.get());
                double basicInterest = pv.get() * rateFactor; 
                if (g.get()==r.get()) {
                    return basicInterest + comp_pmt.get()*n.get()*pow(1+r.get(),n.get()-1);
                } else {
                    return basicInterest + comp_pmt.get()/(r.get()-g.get()) * (rateFactor - pow(1+g.get(),n.get()));
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
                if (pmt.get()==0.0 || n.get() == 0.0) {
                    throw new IllegalArgumentException("cannot solve g with no payments");
                }
                break;
            case n:
                break;
            case comp_pmt:
                break;
            default:
                throw new IllegalArgumentException("not implemented for "+solveFor);
        }
        // didn't have a direct function, so we'll iterate to find a solution
        double d = 
         Math.iterateSolve(x->{
        	Finance f = new Finance();
        	Stream.of("pv","n","r","g","pmt","comp").filter(p->!solveFor.toString().equals(p)).forEach(p->{
        		f.set(p, (Number)get(p));
        	});
            f.set(solveFor,x);
            //System.out.println("setting "+solveFor+" to "+x+" and got "+f.getSolution());
            return f.getSolution();
        },fv.get(),0.0,.00000001,.000001);
        return d;
    } 
    public static final double compoundInterestRate(double presentValue, double futureValue, double periods) {
        return pow(futureValue/presentValue, 1/periods)-1;
    } 
    public static final double compoundInterestNumPeriods(double presentValue, double futureValue, double rate) {
        return log(futureValue/presentValue)/log(1+rate);
    }
    public static void main(String[] args) {
        //System.out.println("simple example: "+growingAnnuityWithCompoundPayments(1.03923, 2, 2, 1.02, 200));
        //System.out.println("simple example: "+growingAnnuityWithCompoundPayments(1.006434, 12, 14, 1.02, 200));
        Finance f = new Finance().r(.08).comp(2).g(.02).n(3).solveFor(TmvParams.pmt).fv(1349.32);
        System.out.println("my finance: "+f);
        System.out.println(f.getSolution());
        f = new Finance().r(.08).comp(2).g(.02).n(3).pmt(200);
        System.out.println("my finance: "+f);
        System.out.println(f.getSolution());
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
