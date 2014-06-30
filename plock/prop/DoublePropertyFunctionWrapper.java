package plock.prop;

import java.util.function.*;

import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;

/* this allows for a bidirectional binding with a function of another double property, such as a simple scaling */
public class DoublePropertyFunctionWrapper extends DoublePropertyBase {
    private final DoubleProperty d;
    private final Function<Double,Double> inv;
    private DoublePropertyFunctionWrapper(
            DoubleProperty orig, Function<Double,Double> f, Function<Double,Double> inv) {
        this.d = orig;
        this.inv=inv;
        // changes to d update this property, which is only bound to the "propertyToBind"
        // since we call super.set(), no loop
        d.addListener((obs, oldVal, newVal) -> super.set(f.apply(newVal.doubleValue())));
    }

    public String getName() {return d.getName();}
    public Object getBean() {return d.getBean();}
    /* this is called when propertyToBind is updated through the binding we set up, so apply the inverse
     * function and set property d while we update
     * @see javafx.beans.property.DoublePropertyBase#set(double)
     */
    public void set(double val) {d.set(inv.apply(val)); super.set(val);}
    /**
     * @dependencies are "third party" variables that are listened to for change.
     *   When they are updated update propertyToBind which is bound
     *   to this which calls set which in turn updates property x with the inverse function.
     * @return propertyToBind to allow for inline creation
    */
    public static DoubleProperty bidirectionalBind(DoubleProperty x, Function<Double,Double> funX,
            Function<Double,Double> invFunX, DoubleProperty propertyToBind, ObservableValue<?> ... dependencies) {
        propertyToBind.set(funX.apply(x.get()));
        propertyToBind.bindBidirectional(new DoublePropertyFunctionWrapper(x, funX, invFunX));
        for (ObservableValue<?> p : dependencies) {
        	p.addListener((obj, oldVal, newVal) -> propertyToBind.set(funX.apply(x.get())));
        }
        return propertyToBind;
    }
};

