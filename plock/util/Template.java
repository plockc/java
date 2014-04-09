package plock.util;

import java.util.*;
import java.lang.Character.*;
import java.nio.file.*;
import java.lang.reflect.*;

// TODO: on demand create the render instance, store the compiled string source code
// TODO: test unicode and octal escaping
// TODO: show where errors are
// TODO: provide binding and handle $
// TODO: Builder pattern of TemplateInstances with defaults for stuff like imports
// TODO: dealing with nulls, some template engines just print the string tag preservation), but we may want the null in some cases
//       or provide a null default at least . . . try to avoid nullpointerexception (I like Chunk rules)

// we set a marker for the entire current variable/expression, in case any part becomes null, I guess a method argument result can be null but an expression can not deref a null.  We need to provide this string representation to the created java source so it can use it in the case of a NullDerefException during evaluation

// if an expression does not complete and end of template, just print out the remainder

/** Text templates as input generate Java source when is then executed to build a string
 * @author Chris Plock - plockc@gmail.com
 */
public class Template {
    /** for ascii character values (less than 256), any non-null entries provide the proper ascii escape */
    private static final Character[] BASIC_ESCAPES = new Character[256];
    private static final boolean[] charsInNumbers = buildMatchingArray("0123456789.");
    private static final boolean[] numberOperators = buildMatchingArray("+-%*/");
    private static final boolean[] booleanOperators = buildMatchingArray("><=&|!");
    private int pos = 0; // the current position
    private int exprStart = 0; // the start of the current expression
    private StringBuilder java = new StringBuilder(); // this is the generated Java source class
    private char[] tpl;
   
    static {
        // initialize the escapes with pairs, the first in the pair is the actual character, the second, the
        // printable character that should come after the backslash
        char[] basicEscapes = new char[] {'\b','b','\t','t','\n','n','\f','f','\r','r','"','"'};
        for (int i=0; i<basicEscapes.length; i+=2) {
            BASIC_ESCAPES[basicEscapes[i]] = basicEscapes[i+1];
        }
    }
    private static boolean[] buildMatchingArray(String matchChars) {
        boolean[] matchingArray = new boolean[256];
        for (int i=0; i<matchChars.length(); i++) {
            matchingArray[matchChars.charAt(i)]=true;
        }
        return matchingArray;
    }
    /** code generated from the template will implement this interface and be executed */
    public interface Renderer { public String render(Map<String,Object> bindings); } 

    private Map<String,Object> bindings = new TreeMap<String,Object>();
    private final Renderer renderer;
    
    public Template(final char[] source) throws Exception {
	this.tpl = source;
        java.append("  StringBuilder out = new StringBuilder();\n  out.append(\n       \"");
        // Here we are processing the textual / non-code part of the template
        // generally copying stuff over, but paying attention to Template escaped characters, java escapes, 
        // binding references, comments, and java code
        while(pos<tpl.length) {
            char c = tpl[pos];
            if (c=='\\') {
                if (pos+1==tpl.length) {throw new IllegalArgumentException("cannot end on an unescaped backslash");}
                // a slash can escape a '{' or a '$' which are special characters in this template, but not for Java
                if (tpl[pos+1] =='{' || tpl[pos+1] =='$') {
                    // we have a Template escaped character, so just add the escaped character all by itself
                    java.append(tpl[++pos]);
                } else {
                    java.append("\\\\"); // a backlash escaped here, and again in the string of java source
                }
                pos++;
            } else if (c=='{') {
                int curPos = pos;
                nextChar();
                if (nextEquals("+\"")) {
                    // TODO: process include
                } else if (nextEquals('$') || nextEquals('+')) {
                    // we are processing a bound variable or a numeric expression
                    consumeChar().append("\");\n");
                    append("  try {out.append(");
                    append(processInlineVariable()).append(");}\n");
                    String templateExpression = new String(tpl,curPos,pos-curPos);
                    append("  catch (Template.BadReferenceException e) {\n");
                    append("    System.out.println(\"failed reference: \"+e); e.printStackTrace();\n");
                    append("    out.append(\""+templateExpression+"\");");
                    append("  }\n");
                    append("  out.append(\n      \"");
                    if (!nextEquals('}')) {throw new ParseException("missing closing '}' from expression:\n"+java);}
                    nextChar();
                }
            } else { 
                // just a character so add it and track if it needs to be escaped
                // control characters come before ' ', anything over '~' are either DEL or an 8 bit character
                //   which can be represented with a UTF-8 escape, and after that, other than '"', everything
                //   else can go straight into a Java double quoted string
                if (BASIC_ESCAPES[c] != null) {
                    if (c == '\n') {
                        java.append("\\n\"\n      +\"");
                    } else {
                        java.append('\\').append(BASIC_ESCAPES[c]);
                    }
                } else if (c>126) { // 126 is '~', 127 is DEL
                    // unicode escape multibyte characters, octal escape single byte characters
                    java.append(c>255 ? "\\u"+Integer.toHexString((int)c) : String.format("%3o",(int)c));
                } else {
                    java.append(tpl[pos]);
                }
                pos++;
            }
        }
        java.append("\");\n  return out.toString();");
        renderer = CompileSourceInMemory.<Renderer>createSimpleInstance(Renderer.class, java.toString());
        if (renderer == null) {
            throw new RuntimeException("failed to compile template: \n------"+new String(tpl)+"\n-----\n"+java);
        }
    }

    private static class ParseException extends RuntimeException {
        ParseException(String message) {super(message);}
    }
    public static class EOFException extends RuntimeException {}
    /** used to tell the java source rendering this template that the expression attempted to use a null value,
     *  and so should just print the expression
     */
    public static class BadReferenceException extends RuntimeException {
        BadReferenceException(String message) {super(message);}
    }
    private enum Type { StringType, BooleanType, NumberType, NoType, BindType};
    private static class Parsed {String before="", java; Type type=NoType;}
    private char nextChar() {if (pos >= tpl.length) {throw new EOFException();} return tpl[pos++];}
    private Template consumeChar() {pos++; return this;}
    private boolean nextEquals(char c) {if (pos >= tpl.length) return false; return tpl[pos]==c;}
    private boolean nextEquals(String s) {
        if (pos+s.length() > tpl.length) return false;
        return new String(tpl, pos, s.length()).equals(s);
    }
    private boolean nextMatches(boolean[] matchSet) {return pos<tpl.length && matchSet[tpl[pos]];}
    private Template appendNext() {if (pos < tpl.length) {java.append(nextChar());} return this;}
    private Template append(Object ... stuff) {for (Object s : stuff) {java.append(s);} return this;}
    private interface Scanner { public boolean scan(char c); } // true when you should stop
    /* start on current letter, scan until true, when returning pos will be ready for nextChar */
    private String scan(Scanner scanner) {
        int start=pos;
        while (pos < tpl.length && !scanner.scan(tpl[pos])) pos++;
        return new String(tpl, start, pos-start);
    }
    private String scanUntilNoMatch(boolean[] matches) {scan((c) -> !matches[c];}
    private String scanUntil(String chars) {
       boolean[] matches = buildMatchingArray(chars); 
       return scan((c) -> matches[c]);
    }
    private Parsed processTerm() {
        if (nextMatches(numberOperators)) {
            return new Parsed() {{java=scanUntilNoMatch(numberOperators); type=NumberType;}};
        }
        if (nextMatches(booleanOperators)) {
            return new Parsed() {{java=scanUntilNoMatch(booleanOperators); type=BooleanType;}};
        }
        if (nextMatches(charsInNumbers)) {
            return new Parsed() {{java=scanUntilNoMatch(charsInNumbers); type=NumberType;}};
        }
        Parsed parsed;
        if (nextEquals('"') ) {
            // TODO somewhat broken
            // TODO process string much like a template
            parsed.type = StringType;
            parsed.java=scanUntil('"');
        } else if (nextEquals('(')) {
            Parsed subExpr = processExpression();
            if (!nextEquals(')')) {throw new ParseException("need closing ')'");}
            parsed.before=parsed.before+subExpr.before;
            parsed.java='('+subExpr.java+')';
            parsed.type=subExpr.type;
        } else if (nextEquals('$')) {
            parsed = processInlineVariable();
        }
        return parsed;
    }
    private Parsed processExpression() {
        List<Parsed> terms = new LinkedList<Parsed>();
        while (!nextEquals('}') && !nextEquals(')') && !nextEquals(',')) {
            terms.add(processTerm());
        }
        Type exprType = NoType;
        for (Parsed parsed : terms) {
            Type termType = parsed.type;
            // Boolean, Number, and Binding can all convert to string concatenation
            if (termType == StringType) {exprType = StringType; break;}
            if (termType == BooleanType) {exprType= BooleanType;}
            if (termType == NumberType) {exprType = NumberType;}
        }
        StringBuilder expr = new StringBuilder();
        for (Parsed term : terms) {
            expr.append(term.before);
            if (term.type != exprType) {expr.append('('+term.type+')');}
            expr.append(term.java);
        }
        return new Parsed() {{java=expr.toString(); type=exprType;}}
    }
    // TODO: this can probably actually be run by processMethod
    /** @return false if null deref and should just print the template expr */
    private Parsed processInlineVariable() {
        // really need to get varName in one shot
        String firstPart = scan((nextChar) -> !Character.isLetter(nextChar));
        String varName = firstPart + scan((nextC) -> nextC=='$' || !Character.isJavaIdentifierPart(nextC));
        // might need to force $ escapes for when this lone '$' shows in nested / complex expressions
        // or throw exception to just print out the expression and produce a warning
        if (varName.length() == 0) {return "$";}
        String codeToEvalToObject = "arg0.get(\""+varName+"\")";
        while (nextEquals('.')) {
            codeToEvalToObject = consumeChar().processMethod(codeToEvalToObject);
        }
        return codeToEvalToObject;
        // check for '$' for recursion variable
    }

    private Parsed processMethod(String codeToEvalToObject) {
        String firstPart = scan((nextChar) -> !Character.isLetter(nextChar));
        String methodName = firstPart + scan((nextC) -> nextC=='$' || !Character.isJavaIdentifierPart(nextC));
        if (methodName.length() == 0) {throw new ParseException("no deference name found");}
        if (nextEquals('.')) {
            // TODO: do other type of fancy derefs like var name, .get("key")
        } else if (nextEquals('(')) {
            consumeChar();
            String call="Template.invoke(\""+methodName+"\", "+codeToEvalToObject+", new Object[] {"+processArgs()+"})";
            consumeChar();
            return new Parsed() {{java=call;}};
        }
        return new Parsed() {{java=methodName;}};
    }
    // TODO need to track if current expr is numeric, even when rest of arg follows
    private String processArgs() {
        Parsed parsed = processExpression();
        if (nextEquals(')')) {return parsed.java;}
        if (nextEquals(',')) { consumeChar(); return arg+","+processArgs(); }
        // the processArgs call will finish the current argument before moving on to the next (or close paren)
        if (nextEquals('$')) { 
            consumeChar();
            return arg+processExpression()+processArgs();
        }
        return "not implemented yet";
    }

    /** this is used by templates to find a reasonable method to call, right now it does
     *  not do a good job of weighting multiple argument options */
    public static Object invoke(String methodName, Object obj, Object[] args) throws BadReferenceException {
        if (obj == null) {throw new BadReferenceException("attempted to call "+methodName+" on a null object");}
        for(Method method : obj.getClass().getMethods()) {
            Class[] paramClasses = method.getParameterTypes();
            if (paramClasses.length != args.length) continue;
            if (!method.getName().equals(methodName)) continue;
            for (int i=0; i<paramClasses.length; i++) {
                if (!paramClasses[i].isAssignableFrom(args[i].getClass())) continue;
            }
            try {
                return method.invoke(obj, args); 
            } catch (IllegalAccessException e) {System.out.println(methodName+" not accessible");
            } catch (InvocationTargetException e) {
                System.out.println("failed to invoke "+methodName); e.printStackTrace();
            }
        }

        // TODO: provide types of parameters
        throw new BadReferenceException("no "+obj.getClass().getSimpleName()+"."+methodName+"() matching arguments");
    }
    public String render() {return renderer.render(bindings);}
    public String render(Map<String,Object> bindings) {
        this.bindings.putAll(bindings);
        return renderer.render(this.bindings);
    }

    /** read the file from first argument given and spit it out to console */
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            Template t= new Template(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8").toCharArray());
            System.out.println(t.render());
        } else {
            final Map<String,Object> bindings = new HashMap<String,Object>() {{
                put("greeting", "world");
                put("exclamation", "!");
                put("one", 1);
                put("two", 2);
                put("three", 3);
            }};
            class Test {public void validate(String template, String result) throws Exception {
                try {
                    Template t = new Template(template.toCharArray());
                    String output = t.render(bindings);
                    if (!output.equals(result)) {
                        throw new RuntimeException(template+"\n-------\n"+t.java+"\n======\nexpected: "+result+"\nGot: "+output);
                    }
                    System.out.println(template+"  -->  "+output);
                } catch (ParseException e) {
                    throw new RuntimeException(template,e);
                }
            }}
            new Test().validate("Hello $greeting", "Hello $greeting");
            new Test().validate("Hello {$greeting}", "Hello world");
            new Test().validate("Hello {$greeting} !", "Hello world !");
            new Test().validate("Hello {$greeting}{$exclamation}", "Hello world!");
            new Test().validate("Hello {$greeting.length()}", "Hello 5");
            new Test().validate("Hello {$greeting.length()} is a lot", "Hello 5 is a lot");
            new Test().validate("Hello {$greeting.substring(1)}", "Hello orld");
            new Test().validate("Hello {$greeting.substring(3-2)}", "Hello orld");
            new Test().validate("Hello {$greeting.substring(1, 3)}", "Hello or");
            new Test().validate("Hello {$greeting.substring(1, 3).length()}", "Hello 2");
            new Test().validate("Hello {$greeting.substring(1, $two)}", "Hello o");
            new Test().validate("Hello {$greeting.substring(3-$two)}", "Hello orld");
            new Test().validate("Hello {$greeting.substring($three-2)}", "Hello orld");
            new Test().validate("Hello {$greeting.substring($three-$two)}", "Hello orld");
            new Test().validate("Hello {$greeting.substring($three-$two+$one)}", "Hello rld");
            new Test().validate("Hello {$greeting.substring(1, $three-$two+$one)}", "Hello or");
            new Test().validate("Hello {$one+$two}", "Hello 3");
            new Test().validate("Hello {$one+2}", "Hello 3");
        }
    }
}

