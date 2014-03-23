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
/** Text templates as input generate Java source when is then executed to build a string
 * @author Chris Plock - plockc@gmail.com
 */
public class Template {
    /** for ascii character values (less than 256), any non-null entries provide the proper ascii escape */
    private static final Character[] BASIC_ESCAPES = new Character[256];
    int pos = 0;
    static {
        // initialize the escapes with pairs, the first in the pair is the actual character, the second, the
        // printable character that should come after the backslash
        char[] basicEscapes = new char[] {'\b','b','\t','t','\n','n','\f','f','\r','r','"','"'};
        for (int i=0; i<basicEscapes.length; i+=2) {
            BASIC_ESCAPES[basicEscapes[i]] = basicEscapes[i+1];
        }
    }
    /** code generated from the template will implement this interface and be executed */
    public interface Renderer { public String render(Map<String,Object> bindings); } 

    private Map<String,Object> bindings = new TreeMap<String,Object>();
    private final Renderer renderer;
    
    public Template(char[] tpl) throws Exception {
        StringBuilder java = new StringBuilder(); // this is the generated Java source class
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
                java.append("\"+");
                processInlineVariable(tpl, java);
                java.append("+\"");
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
        System.out.println(java);
        renderer = CompileSourceInMemory.<Renderer>createSimpleInstance(Renderer.class, java.toString());
    }

    /** @return number of characters processed */
    private void processInlineVariable(char[] tpl, StringBuilder java) {
        if (tpl.length==pos || !Character.isLetter(tpl[pos])) {java.append('$'); return;}
        StringBuilder varName = new StringBuilder().append(tpl[pos++]);
        while(pos<tpl.length) {
            char c = tpl[pos];
            if (!Character.isJavaIdentifierPart(c)) {
                if (c=='.') {
                    java.append("arg0.get(\""+varName+"\").");
                    pos++; processMethod(tpl, java);
                    return;
                }
                break;
            }
            varName.append(c);
            pos++;
        } 
        java.append("arg0.get(\""+varName+"\")");
    }

    private void processMethod(char[] tpl, StringBuilder java) {
        if (tpl.length==pos || !Character.isLetter(tpl[pos])) {return;}
        StringBuilder methodName = new StringBuilder().append(tpl[pos++]);
        while(pos<tpl.length) {
            char c = tpl[pos++];
            if (!Character.isJavaIdentifierPart(c)) {
                if (c=='.') {java.append(methodName).append("()."); pos++; processMethod(tpl, java);}
                else if (c=='(') {
                    java.append(methodName).append('(');
                    pos++;
                    processArg(tpl, java);
                    java.append(')');
                }
                break;
            }
            methodName.append(c);
        } 
    }
    private void processArg(char[] tpl, StringBuilder java) {
        if (tpl.length==pos) {return;}
        if (tpl[pos] != ')') {throw new RuntimeException("non-empty args not supported yet");}
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
                if (!output.equals(result)) { throw new RuntimeException(output); }
                System.out.println(output);
            }}
            new Test().validate("Hello $greeting !", "Hello world !");
            new Test().validate("Hello $greeting.length()", "Hello 5");
        }
    }
}

