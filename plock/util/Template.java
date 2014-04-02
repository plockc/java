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
    private int pos = 0; // the current position
    private int exprStart = 0; // the start of the current expression
    private StringBuilder java = new StringBuilder(); // this is the generated Java source class
    private StringBuilder expr = new StringBuilder(); // current expression in case we try to deref null
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
                if (nextEquals('$')) {
                    consumeChar().append("\");\n");
                    append("  try {out.append(");
                    processInlineVariable().append(");}\n");
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
    private char nextChar() {if (pos >= tpl.length) {throw new EOFException();} return tpl[pos++];}
    private Template consumeChar() {pos++; return this;}
    private boolean nextEquals(char c) {if (pos >= tpl.length) return false; return tpl[pos]==c;}
    private Template appendNext() {if (pos < tpl.length) {java.append(nextChar());} return this;}
    private Template append(Object ... stuff) {for (Object s : stuff) {java.append(s);} return this;}
    private interface Scanner { public boolean scan(char c); } // true when you should stop
    /* start on current letter, when returning pos will be ready for nextChar */
    private String scan(Scanner scanner) {
        int start=pos;
        while (pos < tpl.length && !scanner.scan(tpl[pos])) pos++;
        return new String(tpl, start, pos-start);
    }
    private String scanUntil(String chars) {
       boolean[] matches = buildMatchingArray(chars); 
       return scan((c) -> matches[c]);
    }

    // TODO: this can probably actually be run by processMethod
    /** @return false if null deref and should just print the template expr */
    private Template processInlineVariable() {
        // really need to get varName in one shot
        String firstPart = scan((nextChar) -> !Character.isLetter(nextChar));
        String varName = firstPart + scan((nextC) -> nextC=='$' || !Character.isJavaIdentifierPart(nextC));
        if (varName.length() == 0) {java.append('$'); return this;}
        if(nextEquals('.')) {
            while (nextEquals('.')) {
                consumeChar().processMethod("arg0.get(\""+varName+"\")");
            }
        } else {
            append("arg0.get(\""+varName+"\")");
        }
        return this;
        // check for '$' for recursion variable
    }

    private Template processMethod(String codeToEvalToObject) {
        String firstPart = scan((nextChar) -> !Character.isLetter(nextChar));
        String methodName = firstPart + scan((nextC) -> nextC=='$' || !Character.isJavaIdentifierPart(nextC));
        if (methodName.length() == 0) {throw new ParseException("no deference name found");}
        char c = nextChar();
        if (c == '.') {
            // TODO: do other type of fancy derefs like var name, .get("key")
        } else if (c == '(') {
            append("Template.invoke(\""+methodName+"\", "+codeToEvalToObject+", new Object[] {");
            append(processArgs()).append("})");
        }
        return this;
    }
    private String processArgs() {
        String arg = scanUntil("$,)");
        char c = nextChar();
        if (c == ')') {return arg;}
        if (c == ',') { return arg+","+processArgs(); }
        return "not implemented yet";
        // must be '$'
        // TODO: handle new expression, need to add "," as a possible stop for expression
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
            }};
            class Test {public void validate(String template, String result) throws Exception {
                try {
                    Template t = new Template(template.toCharArray());
                    String output = t.render(bindings);
                    if (!output.equals(result)) {
                        throw new RuntimeException(t.java+"\n======\nexpected: "+result+"\nGot: "+output);
                    }
                    System.out.println(template+"  -->  "+output);
                } catch (ParseException e) {
                    throw new RuntimeException(template,e);
                }
            }}
            new Test().validate("Hello $greeting", "Hello $greeting");
            new Test().validate("Hello {$greeting}", "Hello world");
            new Test().validate("Hello {$greeting}", "Hello world");
            new Test().validate("Hello {$greeting} !", "Hello world !");
            new Test().validate("Hello {$greeting}{$exclamation}", "Hello world!");
            new Test().validate("Hello {$greeting.length()}", "Hello 5");
            new Test().validate("Hello {$greeting.length()} is a lot", "Hello 5 is a lot");
            new Test().validate("Hello {$greeting.substring(1)}", "Hello orld");
            new Test().validate("Hello {$greeting.substring(1, 3)}", "Hello or");
            new Test().validate("Hello {$greeting.substring(1, 3).length()}", "Hello 2");
        }
    }
}

