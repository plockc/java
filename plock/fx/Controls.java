package plock.fx;

import java.text.*;
import javafx.scene.control.*;
import javafx.event.*;
import javafx.util.*;
import javafx.geometry.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;

public class Controls {
    public static class DoubleTextField extends TextField {
        private DoubleProperty val = new SimpleDoubleProperty();
        private IntegerProperty numFractionDigits = new SimpleIntegerProperty(0);
        private IntegerProperty shift = new SimpleIntegerProperty();
        private NumberFormat format = NumberFormat.getInstance();
        public DoubleTextField() {
            setText("0");
            setAlignment(Pos.CENTER_RIGHT);
            getProperties().put("value", val);
            format.setMaximumFractionDigits(numFractionDigits.get());
            format.setGroupingUsed(false);
            textProperty().bindBidirectional(val, new StringConverter<Number>() {
                public Number fromString(String str) {
                    if (str.length()==0 || str.equals(".") || str.equals("-")) {
                        return 0.0;
                    }
                    try {
                        return format.parse(str).doubleValue()*Math.pow(10,-shift.get());
                    } catch (ParseException e) {
                        System.out.println("doh on "+str+": "+e);
                        return 0.0;
                    }
                }
                public String toString(Number d) {return format.format(d.doubleValue()*Math.pow(10,shift.get()));}
            });                   
        } 
        public DoubleProperty valProperty() {return val;}
        public IntegerProperty shiftProperty() {return shift;}
        public IntegerProperty numFractionDigitsProperty() {return numFractionDigits;}
        public int getShift() {return shift.get();}
        public int getNumFractionDigits() {return numFractionDigits.get();}
        public double getVal() {return val.get();}
        public void setVal(int val) {this.val.set(val);}
        public void setShift(int shift) {this.shift.set(shift);}
        public void setNumFractionDigits(int digits) {this.numFractionDigits.set(digits);}

        @Override public void replaceText(int start, int end, String text) {
            // If the replaced text would end up being invalid, then simply
            // ignore this call!
            update(start, end, text, () -> super.replaceText(start, end, text));
        }
    
        @Override public void replaceSelection(String text) {
            int min = Math.min(getCaretPosition(), getAnchor()); 
            int max = Math.max(getCaretPosition(), getAnchor()); 
            update(min, max, text, () -> super.replaceSelection(text));
        }
        private void update(int start, int end, String text, Runnable run) {
            String old = getContent().get();
            StringBuilder str = new StringBuilder().append(old.substring(0,start));
            str.append(text).append(old.substring(end, old.length()));
            String newStr = str.toString();
            if (newStr.length()!=0 && !newStr.equals(".") && !newStr.equals("-")) {
                char last = newStr.charAt(newStr.length()-1);
                if (last != '.' && (last < '0' || last > '9')) {
                    return; // Double.parseDouble can handle f and d at the end
                }
                try { 
                    Double.parseDouble(newStr); 
                } catch (NumberFormatException e) {
                    return;
                }
            }
            try { run.run(); } catch (RuntimeException e) { throw e; }
        }
    }

    /** 
     * thanks for the help http://fxexperience.com/2012/02/restricting-input-on-a-textfield/
     */
    public static TextField createDoubleField(DoubleProperty prop, int numFractionDigits, int shift) {
        DoubleTextField f = new DoubleTextField();
        f.valProperty().bindBidirectional(prop);
        f.setNumFractionDigits(numFractionDigits);
        f.setShift(shift);
        return f;
    }
}

