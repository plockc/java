package plock.prop;

import java.util.function.*;
import javafx.beans.property.*;

/* this allows for a bidirectional binding with a function of another double property, such as a simple scaling */
public class DoublePropertyFunctionWrapper extends DoublePropertyBase {
    private final DoubleProperty d, modified;
    Function<Double,Double> f, inv;
    private DoublePropertyFunctionWrapper(
            DoubleProperty orig, Function<Double,Double> f, Function<Double,Double> inv, DoubleProperty modified) {
        this.d = orig;
        this.modified = modified;
        this.f=f;
        this.inv=inv;
        d.addListener((obs, oldVal, newVal) -> super.set(f.apply(newVal.doubleValue())));
    }

    public String getName() {return d.getName();}
    public Object getBean() {return d.getBean();}
    public void set(double val) {d.set(inv.apply(val)); super.set(val);}

    /** @return propertyToBind to allow for inline creation
    */
    public static DoubleProperty bidirectionalBind(DoubleProperty x, Function<Double,Double> funX,
            Function<Double,Double> invFunX, DoubleProperty propertyToBind) {
        propertyToBind.bindBidirectional(new DoublePropertyFunctionWrapper(x, funX, invFunX, propertyToBind));
        return propertyToBind;
    }
};

