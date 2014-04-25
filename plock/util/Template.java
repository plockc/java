package plock.util;

import java.util.*;
import java.util.function.Function;
import java.lang.Character.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.stream.*;

// TODO: test unicode and octal escaping
// TODO: show where errors are
// TODO: dealing with nulls, some template engines just print the string tag preservation), but we may want the null in some cases
//       or provide a null default at least . . . try to avoid nullpointerexception (I like Chunk rules)

// if doing binary expressions with just bindings, then to get floating point, you must add +0.0f as a hint
/** Text templates as input generate Java source when is then executed to build a string
 * @author Chris Plock - plockc@gmail.com
 */
public class Template {
    /** for ascii character values (less than 256), any non-null entries provide the proper ascii escape */
    private static final Character[] BASIC_ESCAPES = new Character[256];
    private static final Character[] BASIC_ESCAPE_CHARS = new Character[256];
    private static final boolean[] charsInNumbers = buildMatchingArray("0123456789.");
    private static final boolean[] numberOperators = buildMatchingArray("+-%*/");
    private static final boolean[] booleanOperators = buildMatchingArray("><=&|!");
    private static final boolean[] whitespace = buildMatchingArray(" \t\n\r");
    /** code generated from the template will implement this interface and be executed */
    public interface Renderer { public String render(Map<String,Object> bindings); } 
    private int pos = 0; // the current position
    private int exprStart = 0; // the start of the current expression
    private StringBuilder java = new StringBuilder(); // this is the generated Java source class
    private String javaSource;
    private char[] tpl;
    private static final Set<String> defaultStaticImports = new HashSet<String>(Arrays.asList(
                "java.lang.Math.*","java.util.Arrays.*", "java.util.Collections.*"));
    private static final Set<String> defaultImports = new HashSet<String>(Arrays.asList(
                "java.lang.*", "java.util.*", "java.util.regex.*", "java.time.*"));
    private Set<String> imports = new HashSet<String>(defaultImports);
    private Set<String> staticImports = new HashSet<String>(defaultStaticImports);
    public static Set<Class> intPrimitiveTypes = Collections.unmodifiableSet(new HashSet<Class>(Arrays.asList(Integer.TYPE, Long.TYPE,
            Short.TYPE, Byte.TYPE, Character.TYPE)));
    public static Set<Class> intTypes = Collections.unmodifiableSet(Stream.concat(intPrimitiveTypes.stream(), Stream.of(Integer.class,
                Short.class, Byte.class, Long.class)).collect(Collectors.toSet()));
    public static Set<Class> floatPrimitiveTypes = Collections.unmodifiableSet(new HashSet<Class>(Arrays.asList(Float.TYPE, Double.TYPE)));
    public static Set<Class> floatTypes = Collections.unmodifiableSet(new HashSet<Class>(Arrays.asList(
            Float.TYPE, Float.class, Double.TYPE, Double.class)));
    private static Map<Class,Class> primitiveToWrapper = new HashMap<Class,Class>() {{
        put(Double.TYPE, Double.class); put(Integer.TYPE, Integer.class); put(Long.TYPE, Long.class); put(Short.TYPE, Short.class);
        put(Float.TYPE, Float.class); put(Character.TYPE, Character.class); put(Short.TYPE, Short.class); put(Byte.TYPE, Byte.class);}};
    private static final TreeMap<Number,Class> numberRanges = new TreeMap<Number,Class>((one,two)->{
        double d1 = one.doubleValue(), d2=two.doubleValue(); return d1==d2 ? 0 : (d1>d2?1:-1);
    });
    private interface NumberCast {public Number cast(Number num);}
    private static final Map<Class,Function<Number,Number>> castingFunctions = new HashMap<Class,Function<Number,Number>>();
    static {
        // initialize the escapes with pairs, the first in the pair is the actual character, the second, the
        // printable character that should come after the backslash
        char[] basicEscapes = new char[] {'\b','b','\t','t','\n','n','\f','f','\r','r','"','"'};
        for (int i=0; i<basicEscapes.length; i+=2) {
            BASIC_ESCAPES[basicEscapes[i]] = basicEscapes[i+1];
        }
        char[] charsNeedingEscaping = new char[] {'\t', 't', '\n', 'n', '\r', 'r', '"', '"'};
        for (int i=0; i<charsNeedingEscaping.length; i+=2) {
            BASIC_ESCAPE_CHARS[charsNeedingEscaping[i]] = charsNeedingEscaping[i+1];
        }
        numberRanges.put(Long.MIN_VALUE, Long.class); numberRanges.put(Long.MAX_VALUE, Long.class);
        numberRanges.put(Integer.MIN_VALUE, Integer.class); numberRanges.put(Integer.MAX_VALUE, Integer.class);
        numberRanges.put(Short.MIN_VALUE, Short.class); numberRanges.put(Short.MAX_VALUE, Short.class);
        numberRanges.put(Byte.MIN_VALUE, Byte.class); numberRanges.put(Byte.MAX_VALUE, Byte.class);
        numberRanges.put(-Double.MAX_VALUE, Double.class); numberRanges.put(Double.MAX_VALUE, Double.class);
        numberRanges.put(-Float.MAX_VALUE, Float.class); numberRanges.put(Float.MAX_VALUE, Float.class);
        castingFunctions.put(Long.class, n->n.longValue());
        castingFunctions.put(Integer.class, n->n.intValue());
        castingFunctions.put(Short.class, n->n.shortValue());
        castingFunctions.put(Byte.class, n->n.byteValue());
        castingFunctions.put(Float.class, n->n.floatValue());
        castingFunctions.put(Double.class, n->n.doubleValue());
    }
    private static boolean[] buildMatchingArray(String matchChars) {
        boolean[] matchingArray = new boolean[256];
        for (int i=0; i<matchChars.length(); i++) {
            matchingArray[matchChars.charAt(i)]=true;
        }
        return matchingArray;
    }

    public String getJava() {return javaSource;}

    public Template() {}
    public Template setSource(final char[] source) throws Exception {
        this.tpl = source;
        java.append("  StringBuilder out = new StringBuilder();\n  out.append(\n       \"");
        // Here we are processing the textual / non-code part of the template
        // generally copying stuff over, but paying attention to Template escaped characters, java escapes, 
        // binding references, comments, and java code
        while(pos<tpl.length) {
            char c = tpl[pos];
            if (c=='\\') {
                if (pos+1==tpl.length) {throw new ParseException("cannot end on an unescaped backslash");}
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
                if (nextOneOf("$+\"-") || Character.isDigit(tpl[pos])) {
                    append("\");\n");
                    append("  try {");
                    if (nextEquals("+\"")) {
                        // TODO: process include
                    } else {
                        // we are processing a bound variable or a numeric expression
                        Parsed expression = processExpression();
                        if (expression.assignment) {
                            append(expression.java).append(";");
                        } else {
                            append("out.append(").append(expression.java).append(");");
                        }
                    }
                    consume(whitespace);
                    if (!nextEquals('}')) {throw new ParseException("missing closing '}' from expression:\n"+java);}
                    consumeChar();
                    // from the saved position, this creates a java string of the expression we just parsed to use in case there 
                    //   is a runtime rendering problem
                    StringBuilder templateExpression = new StringBuilder();
                    for (char ch=tpl[curPos++]; curPos<pos; ch=tpl[curPos++]) {
                        templateExpression.append(BASIC_ESCAPE_CHARS[ch]!=null ? "\\"+BASIC_ESCAPE_CHARS[ch] : ch);
                    }
                    append("}\n");
                    append("  catch (Template.BadReferenceException e) {\n");
                    append("    System.out.println(\"failed reference: \"+e); e.printStackTrace();\n");
                    append("    out.append(\""+templateExpression+"\");");
                    append("  }\n");
                    append("  out.append(\n      \"");
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
        javaSource=java.toString();
        java=null;
        return this;
    }

    public Set<String> getStaticImports() {return new HashSet<String>(imports);}
    public Template setStaticImports(Collection<String> imports) {
        this.staticImports = new HashSet<String>(imports); return this;
    }
    public Set<String> getImports() {return new HashSet<String>(imports);}
    public Template setImports(Collection<String> imports) {this.imports=new HashSet<String>(imports); return this;}
    public Template addImports(Collection<String> importsToAdd) {imports.addAll(importsToAdd); return this;}

    private static class ParseException extends RuntimeException {
        ParseException(String message) {super(message);}
        ParseException(String message, Throwable t) {super(message, t);}
    }
    public static class EOFException extends RuntimeException {}
    /** used to tell the java source rendering this template that the expression attempted to use a null value,
     *  and so should just print the expression
     */
    public static class BadReferenceException extends RuntimeException {
        BadReferenceException(String message) {super(message);}
    }
    private enum Type {
        StringType, BooleanType, LongType, DoubleType, NoType, BindType;
        public String toString() { return name().replaceAll("Type", ""); }
    }
    private static class Parsed {String java; Type type=Type.NoType; boolean assignment=false;}
    private char nextChar() {if (pos >= tpl.length) {throw new EOFException();} return tpl[pos++];}
    private char currentChar() {return tpl[pos];}
    private Template consumeChar() {pos++; return this;}
    private Template consumeChars(int num) {pos+=num; return this;}
    private Template consume(boolean[] charsToEat) {while (charsToEat[tpl[pos]]) pos++; return this;}
    private boolean nextEquals(char c) {if (pos >= tpl.length) return false; return tpl[pos]==c;}
    private boolean nextEquals(String s) { return (pos+s.length() <= tpl.length) && new String(tpl, pos, s.length()).equals(s); }
    private boolean nextOneOf(String validChars) {return validChars.indexOf(tpl[pos]) != -1;}
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
    private String scanUntilNoMatch(boolean[] matches) {return scan((c) -> !matches[c]);}
    private String scanUntil(String chars) {
       boolean[] matches = buildMatchingArray(chars); 
       return scan((c) -> matches[c]);
    }
    private String scanBindingName() {
        String binding = scan((nextChar) -> !Character.isLetter(nextChar))
            +  scan((nextC) -> nextC=='$' || !Character.isJavaIdentifierPart(nextC));
        if (binding.length() == 0) {throw new ParseException("no deference name found");}
        return binding;
    }
     private Parsed processTerm() {
        consume(whitespace);
        if (nextMatches(numberOperators)) {
            return new Parsed() {{java=scanUntilNoMatch(numberOperators); type=Type.LongType;}};
        }
        if (nextMatches(booleanOperators)) {
            return new Parsed() {{java=scanUntilNoMatch(booleanOperators);}};
        }
        if (nextMatches(charsInNumbers)) {
            String number = scanUntilNoMatch(charsInNumbers);
            return new Parsed() {{java=number; type=number.contains(".") ? Type.DoubleType: Type.LongType;}};
        }
        Parsed parsed = new Parsed();
        if (nextEquals('"') ) {
            // TODO somewhat broken
            // TODO process string much like a template
            parsed.type = Type.StringType;
            parsed.java='"'+consumeChar().scanUntil("\"")+'"';
            consumeChar();
        } else if (nextEquals('(')) {
            consumeChar();
            Parsed subExpr = processExpression();
            if (!nextEquals(')')) {throw new ParseException("need closing ')'");}
            parsed.java='('+subExpr.java+')';
            parsed.type=subExpr.type;
            consumeChar();
        } else if (nextEquals('$')) {
            parsed = consumeChar().processInlineVariable();
        } else {
           throw new ParseException("illegal character '"+currentChar()+"'");
        }
        return parsed;
    }
    private Parsed processExpression() {
        List<Parsed> terms = new LinkedList<Parsed>();
        while (!nextEquals('}') && !nextEquals(')') && !nextEquals(',')) {
            terms.add(processTerm());
            consume(whitespace);
        }
        Parsed expr = new Parsed();
        for (Parsed parsed : terms) {
            Type termType = parsed.type;
            if (parsed.assignment && terms.size()>1 && !parsed.java.startsWith("(")) {
                throw new ParseException("must wrap assignment with () in an expression");
            }
            // Boolean, Number, and Binding can all convert to string concatenation
            if (termType == Type.StringType) {expr.type = Type.StringType; break;}
            if (termType == Type.BooleanType) {expr.type= Type.BooleanType;}
            if (termType == Type.LongType && expr.type!=Type.DoubleType) {expr.type = Type.LongType;}
            if (termType == Type.DoubleType) {expr.type = Type.DoubleType;}
            if (termType == Type.BindType && expr.type==Type.NoType) {expr.type = Type.BindType;}
        }
        final StringBuilder exprBuilder = new StringBuilder();
        for (Parsed term : terms) {
            if (expr.type != Type.NoType && expr.type != Type.BindType && term.type == Type.BindType) {
                if (expr.type == Type.LongType) {
                    exprBuilder.append("((Number)("+term.java+")).longValue()");
                } else if (expr.type == Type.DoubleType) {
                    exprBuilder.append("((Number)("+term.java+")).doubleValue()");
                } else {
                    exprBuilder.append('('+expr.type.toString()+')').append(term.java);
                }
            } else {
                exprBuilder.append(term.java);
            }
        }
        expr.assignment = terms.size() == 1 && terms.get(0).assignment; 
        expr.java=exprBuilder.toString();
        return expr;
    }
    // TODO: this can probably actually be run by processMethod
    // TODO: this should be renamed to process binding
    /** @return false if null deref and should just print the template expr */
    private Parsed processInlineVariable() {
        Parsed codeToEvalToObject = new Parsed(); codeToEvalToObject.type=Type.BindType;
        if (nextEquals("(i)") || nextEquals("(f)")) {
            codeToEvalToObject.type = nextEquals("(i)") ? Type.LongType : Type.DoubleType;
            consumeChars(3);
        }
        String varName = scanBindingName();
        // TODO: how can i tell a static included method's type?
        // should be able to get fully qualified class and the return type!
        // -- what about a instance method call, would need a set of methods
        //    to be programmatically set up with (name, modifiers, code) so we can identify and get type information
        // ** remember we need type information, the defined package includes also provide authorization
        if (nextEquals('(')) {
            // TODO: search for method in static includes and try local functions
        } else if (nextEquals("::")) {
            consumeChar().consumeChar();
            codeToEvalToObject = processStaticMethod(varName);
        } else if (nextEquals("=") && !nextEquals("==")) {
            Parsed parsedValue = consumeChar().processExpression();
            if (parsedValue.java.length() == 0) {throw new ParseException("invalid assignment value for "+varName);}
            parsedValue.java="Template.assign(arg0, \""+varName+"\", "+parsedValue.java+")";
            parsedValue.type=Type.BindType;
            parsedValue.assignment=true;
            return parsedValue;
        } else {
            String bound = "arg0.get(\""+varName+"\")";
            if (codeToEvalToObject.type != Type.BindType) {
                String className = (codeToEvalToObject.type == Type.LongType ? "Long" : "Double");
                String java = "((java.util.function.Function<Number,"+className+">)n->"+className+".class.equals(n) ? ("+className+")n : ((Number)n)."+className.toLowerCase()+"Value()).apply((Number)arg0.get(\""+varName+"\"))";
                codeToEvalToObject.java = java;
            } else {
                codeToEvalToObject.java = bound;
            }
        }
        while (nextEquals('.')) {
            codeToEvalToObject = consumeChar().processMethod(codeToEvalToObject.java);
        }
        if (nextEquals("::")) {throw new ParseException("static methods can only be called on imported classes and not inner classes");}
        return codeToEvalToObject;
        // TODO: check for '{$' for recursion variable
    }

    private Parsed processStaticMethod(String varName) {
        final String methodName = scanBindingName();
        if (!nextEquals('(')) {throw new ParseException("don't yet support static member variables");}
        consumeChar();
        for (String packageName : imports) {
            String packagePath = packageName.replaceAll("\\.","/");
            if (packagePath.endsWith("*")) {
                packagePath = '/'+packagePath.replaceAll("\\*","");
                if (getClass().getResourceAsStream(packagePath+varName+".class") != null) {
                    varName = packageName.substring(0,packageName.length()-1)+varName;
                    break;
                }
                continue;
            } else {
                if (!packageName.endsWith('.'+varName)) {
                    continue;
                }
                varName = packageName;
                break;
            }
        }
        try {
            Class clazz = Class.forName(varName);
            if (clazz == null) {throw new ParseException("did not find class "+varName);}
            Method method = Arrays.stream(clazz.getMethods()).filter(m->m.getName().equals(methodName))
                .findFirst().orElse(null);
            if (method == null) {throw new ParseException("no method in "+varName+" called "+methodName);}
            // TODO: check return type and set properly
            String args = "new Object[] {"+processArgs()+"}";
            final String invocation = "Template.invoke(\""+methodName+"\", "+varName+".class, null, "+args+")";
            if (!nextEquals(')')) {throw new ParseException("missing closing ')'");}
            consumeChar();
            return new Parsed() {{ java = invocation; type=Type.BindType; }};
        } catch (ClassNotFoundException e) {throw new ParseException("class "+varName+" not found: "+e.getMessage(), e);}
     }
    private Parsed processMethod(String codeToEvalToObject) {
        String methodName = scanBindingName();
        if (nextEquals('.')) {
            // TODO: do other type of fancy derefs like var name, .get("key")
        } else if (nextEquals('(')) {
            consumeChar();
            String call="Template.invoke(\""+methodName+"\", "+codeToEvalToObject+", new Object[] {"+processArgs()+"})";
            consumeChar();
            return new Parsed() {{java=call; type=Type.BindType;}};
        }
        return new Parsed() {{java=methodName; type=Type.BindType;}};
    }
    private String processArgs() {
        Parsed parsed = processExpression();
        if (nextEquals(')')) {return parsed.java;}
        if (nextEquals(',')) { consumeChar(); return parsed.java+","+processArgs(); }
        // the processArgs call will finish the current argument before moving on to the next (or close paren)
        if (nextEquals('$')) { 
            consumeChar();
            return parsed.java+processExpression()+processArgs();
        }
        return "not implemented yet";
    }

    public static Object assign(Map<String,Object> bindings, String bindingName, Object value) {
        bindings.put(bindingName, value);
        return value;
    }
    /** this is used by templates to find a reasonable method to call, right now it does
     *  not do a good job of weighting multiple argument options */
    public static Object invoke(String methodName, Object obj, Object[] args) throws BadReferenceException {
        if (obj == null) {throw new BadReferenceException("attempted to call "+methodName+" on a null object");}
        return invoke(methodName, obj.getClass(), obj, args);
    }
    // TODO: deal with null as an argument, can map to any non primitize class type
    public static Object invoke(String methodName, Class clazz, Object obj, Object[] args) throws BadReferenceException {
        Class[] argClasses = Arrays.stream(args).map(a->a.getClass()).toArray(size->new Class[size]);
        //System.out.println("invoking with "+methodName+"("+Arrays.stream(argClasses).map(c->c.getSimpleName()).collect(Collectors.joining(", "))+")");
        Optional<Method> method = Optional.empty();
        // try to grab with exact match on parameters
        try {method = Optional.of(clazz.getMethod(methodName, argClasses));} catch (NoSuchMethodException ignored) {}
        class MethodMeta {Method method; int rank=0; Object[] mappedArgs = new Object[args.length];}
        // if couldn't get an exact method, we have to search through them all
        MethodMeta invocationMeta = Arrays.stream(clazz.getMethods())
            .filter(m->m.getParameterTypes().length == args.length && m.getName().equals(methodName))
            .map(m->{
                MethodMeta methodMeta = new MethodMeta();
                methodMeta.method=m; 
                Class[] paramClasses = m.getParameterTypes();
               // System.out.println("  testing "+methodName+"("+
               //     Arrays.stream(m.getParameterTypes()).map(p->p.getSimpleName()).collect(Collectors.joining(", "))+")");
                for (int i=0; i<paramClasses.length; i++) {
                    methodMeta.mappedArgs[i] = args[i];
                    Class paramClass = paramClasses[i], argClass = argClasses[i];
                    if (paramClass.equals(argClass)) {continue;}
                    if (Number.class.isAssignableFrom(argClass)) {
                        if (paramClass.isPrimitive()) {paramClass = primitiveToWrapper.get(paramClass);}
                        if (argClass.equals(paramClass)) {continue;} // in case primitive method param was just promoted to wrapper
                        if (intTypes.contains(paramClass) && floatTypes.contains(argClass)) {
                            //System.out.println("    cannot convert "+argClass.getSimpleName()+" to "+paramClass.getSimpleName());
                            return null;
                        }
                        // check if arg is float or double, and then if param is int type, if so, OK but add to rank
                        if (intTypes.contains(argClass) && floatTypes.contains(paramClass)) {
                            //System.out.println("    converting non-decimal "+argClass.getSimpleName()+" to "+paramClass.getSimpleName());
                            methodMeta.rank+=2;
                            methodMeta.mappedArgs[i] = castingFunctions.get(paramClass).apply((Number)args[i]);
                        } else {
                            Number num = (Number)args[i];
                            SortedMap wideningTypes = null;
                            if (num.floatValue() >= 0.0f) {
                                //System.out.println("    pos "+num);
                                wideningTypes = numberRanges.subMap(num, true, Double.MAX_VALUE, true);
                            } else {
                                //System.out.println("    neg "+num);
                                wideningTypes = numberRanges.subMap(-Double.MAX_VALUE, true, num, true);
                            }
                            if (!wideningTypes.containsValue(paramClass)) {
                                //System.out.println("    cannot do narrowing cast from "+args[i]+" to "+paramClass.getSimpleName());
                                return null;
                            }
                            methodMeta.mappedArgs[i] = castingFunctions.get(paramClass).apply(num);
                            methodMeta.rank++;
                            //System.out.println("    casting to : "+methodMeta.mappedArgs[i].getClass().getSimpleName());
                        }
                    } else {
                        if (!paramClasses[i].isAssignableFrom(argClasses[i])) {
                            return null;
                        }
                        methodMeta.rank+=2;
                    }
                }
                //System.out.println("    processed "+methodName+"("+
                //        Arrays.stream(methodMeta.method.getParameterTypes()).map(p->p.getSimpleName()).collect(Collectors.joining(", "))
                //        +") with score "+methodMeta.rank);


                return methodMeta;
            })
            .filter(mm-> mm != null)
            .min(Comparator.comparingInt((MethodMeta rankMeta)->rankMeta.rank))
            .orElseThrow(() -> {
                String params = Arrays.stream(argClasses).map(c->c.getSimpleName()).collect(Collectors.joining(", "));
                return new BadReferenceException("no "+clazz.getSimpleName()+"."+methodName+"() matching ("+params+")");
            });
        //System.out.print("invoking "+methodName+"("+Arrays.stream(invocationMeta.mappedArgs).map(a->a.getClass().getSimpleName()).collect(Collectors.joining(", "))+") with (");
        //System.out.print(Arrays.stream(invocationMeta.method.getParameterTypes()).map(p->p.getSimpleName()).collect(Collectors.joining(", "))+")");
        //System.out.println(" of "+Arrays.asList(invocationMeta.mappedArgs)+")");
        try {
            return invocationMeta.method.invoke(obj, invocationMeta.mappedArgs); 
        } catch (IllegalAccessException e) {System.out.println(methodName+" not accessible");
        } catch (InvocationTargetException e) {
            // TODO: better error handling, show the original expression
            System.out.println("failed to invoke "+methodName); e.printStackTrace();
        }
        return null;
    }
    public String render(Map<String,Object> bindings) throws Exception {
        Renderer renderer = CompileSourceInMemory.<Renderer>createSimpleInstance(Renderer.class, javaSource);
        if (renderer == null) {
            throw new RuntimeException("failed to compile template: \n------"+new String(tpl)+"\n-----\n"+java);
        }
        return renderer.render(new HashMap<String,Object>(bindings));
    }

    /** read the file from first argument given and spit it out to console */
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            Template t= new Template().setSource(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8").toCharArray());
            System.out.println(t.render(Collections.emptyMap()));
        } else {
            final Map<String,Object> bindings = new HashMap<String,Object>() {{
                put("greeting", "world");
                put("exclamation", "!");
                put("one", 1);
                put("two", 2);
                put("three", 3);
                put("fivePoint1", 5.1);
                put("fivePoint2", 5.2);
            }};
            class Test {public void validate(String template, String result) throws Exception {
                Template t = null;
                try {
                    t = new Template().setSource(template.toCharArray());
                } catch (ParseException e) {
                    throw new RuntimeException(template,e);
                }
                try {
                    String output = t.render(bindings);
                    if (!output.equals(result)) {
                        throw new RuntimeException("expected: "+result+"\nGot: "+output);
                    }
                    System.out.println(template+"  -->  "+output);
                } catch (Exception e) {
                    throw new RuntimeException(template+"\n-----\n"+t.javaSource+"\n-----\n"+e.getMessage(), e);
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
            new Test().validate("Hello {$greeting.substring(1, $three-$two+$one)}", "Hello o");
            new Test().validate("Hello {$Math::min($two,$one)}", "Hello 1");
            new Test().validate("Hello {$Math::min(5,3)}", "Hello 3");
            new Test().validate("Hello {$Math::min(5,5.2)}", "Hello 5.0");
            new Test().validate("Hello {$Math::min(5.1,5.2)}", "Hello 5.1");
            new Test().validate("Hello {$Math::min(5.2,5.1)}", "Hello 5.1");
            new Test().validate("Hello {$Math::min($fivePoint2,$fivePoint1)}", "Hello 5.1");
            new Test().validate("Hello {$Math::min($fivePoint1,$fivePoint2)}", "Hello 5.1");
            new Test().validate("Hello {$Math::min(-5.2,5.1)}", "Hello -5.2");
            new Test().validate("Hello {$Math::min(-5,5.1)}", "Hello -5.0");
            new Test().validate("Hello {$Math::min($fivePoint2,5.1)}", "Hello 5.1");
            new Test().validate("Hello {$two+-1}", "Hello 1");
            new Test().validate("Hello {$one+$two}", "Hello 3");
            new Test().validate("Hello {$one+2}", "Hello 3");
            new Test().validate("Hello {$two-(-1)}", "Hello 3");
            // TODO: this is getting cast to long as part of numeric expression of unknown type, need float hint
            new Test().validate("Hello {$Math::min(-$(f)fivePoint2,5.1)}", "Hello -5.2");
            new Test().validate("Hello {5-(-3)}", "Hello 8");
            new Test().validate("Hello {5+-3}", "Hello 2");
            // need to validate parse exception
            // new Test().validate("Hi {$Math.min(3,2);", "Hi {$Math.min(3,2);");
            // need to validate that this fails to compile, which is the same that java does
            // new Test().validate("Hello {5--3}", "Hello {5--3}");
            //new Test().validate("Hello {$one+$num=2} {$num} 1", "Hello 3 2 1");
            new Test().validate("Hello {$msg=\"world\"}{$msg}", "Hello world");
            new Test().validate("Hello {$one+($num=2)} {$num} 1", "Hello 3 2 1");
        }
    }
}

