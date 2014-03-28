package plock.util;

import java.util.*;
import java.lang.Character.*;
import java.nio.file.*;

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
            } else if (c=='$') {
                pos++;
                java.append("\"+(");
                processInlineVariable();
                java.append(")+\"");
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
	if (renderer == null) {throw new RuntimeException("failed to compile template: \n"+java);}
    }

    private static class ParseException extends RuntimeException {}
    public static class EOFException extends RuntimeException {}
    private char nextChar() {if (pos >= tpl.length) {throw new EOFException();} return tpl[pos++];}
    private interface Scanner { public boolean scan(char c); } // true when you should stop
    /* start on current letter, when returning pos will be on last letter, ready for nextChar */
    private String scan(Scanner scanner) {
	int start=pos;
	while (pos < tpl.length && !scanner.scan(tpl[pos++]));
	return new String(tpl, start, --pos-start);
    }
    private char copyUntil(String chars) {
	boolean[] stopChars = buildMatchingArray(chars);
	String copied = scan((c) -> stopChars[c]);
	java.append(copied);
	expr.append(copied);
	return nextChar();
    }

    /** @return false if null deref and should just print the template expr */
    private void processInlineVariable() {
	// really need to get varName in one shot
	String firstPart = scan((nextChar) -> !Character.isLetter(nextChar));
	String varName = firstPart + scan((nextC) -> !Character.isJavaIdentifierPart(nextC));
	if (varName.length() == 0) {java.append('$'); return;}
        java.append("arg0.get(\""+varName+"\")");
	if (tpl[pos] == '.') {
	    java.append(nextChar()); // this is the '.'
	    processMethod();
	}
	// check for '$' for recursion variable
    }

    private void processMethod() {
	String firstPart = scan((nextChar) -> !Character.isLetter(nextChar));
	String methodName = firstPart + scan((nextC) -> !Character.isJavaIdentifierPart(nextC));
	if (methodName.length() == 0) {throw new RuntimeException("no deference name found");}
	char c = nextChar();
	if (c == '.') {
	    // TODO: do other type of fancy derefs like var name, .get("key")
	    java.append(methodName).append("()."); 
	    pos++;
	    processMethod();
	} else if (c == '(') {
	    java.append(methodName).append('(');
	    processArgs();
	    java.append(')');
	}
        return;
    }
    private boolean processArgs() {
	boolean noNullDeref = true;
	char c = copyUntil("$,)");
	if (c == ')') {return noNullDeref;}
	if (c == ',') {
	    java.append(','); expr.append(',');
	    return processArgs();
	}
	return false;
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
            final Map<String,Object> bindings = new HashMap<String,Object>() {{put("greeting", "world");}};
            class Test {public void validate(String template, String result) throws Exception {
                Template t = new Template(template.toCharArray());
                String output = t.render(bindings);
                if (!output.equals(result)) {
		    throw new RuntimeException(t.java+"\n======\nexpected: "+result+"\nGot: "+output);
		}
                System.out.println(output);
            }}
            new Test().validate("Hello $greeting !", "Hello world !");
            new Test().validate("Hello $greeting.length()", "Hello 5");
        }
    }
}

