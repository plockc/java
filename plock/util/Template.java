package plock.util;

import java.util.*;
import java.nio.file.*;

// TODO: test unicode and octal escaping
// TODO: handle $
// TODO: Builder pattern of TemplateInstances with defaults for stuff like imports
public class Template {
    private static final int BUFF_LENGTH = 1000;
    private static final Character[] BASIC_ESCAPES = new Character[256];
    private char[] buff = new char[BUFF_LENGTH];
    private int buffI = 0;
    private List<Integer> escapes = new LinkedList<Integer>();
    static {
        char[] basicEscapes = new char[] {'\b','b','\t','t','\n','n','\f','f','\r','r','"','"'};
        for (int i=0; i<basicEscapes.length; i+=2) {
            BASIC_ESCAPES[basicEscapes[i]] = basicEscapes[i+1];
        }
    }
    public interface Renderer { public String render(); } 
    private final Renderer renderer;
    private StringBuilder java = new StringBuilder();

    public Template(char[] tpl) throws Exception {
        java.append("  StringBuilder out = new StringBuilder();\n  out.append(\n       \"");
        int start=0, end = tpl.length;
        for (int i=start; i<end; i++) {
            char c = tpl[i];
            if (c=='\\') {
                if (i+1==tpl.length || (tpl[i+1] !='{' && tpl[i+1] !='$')) {
                    // last char or it's not an escape sequence, so just add it
                    buff[buffI++] = '\\';
                    buff[buffI++] = tpl[i];
                } else { // the next char was escaped sequence from the template, just add it
                    buff[buffI++] = tpl[++i];
                }
            } else if (c=='$') {
                
            } else { // just a character so add it and track if it needs to be escaped
                if (c<' ' || c>'~' || c=='"') {
                    escapes.add(buffI); 
                }
                buff[buffI++] = tpl[i];
            }
            if (buffI==BUFF_LENGTH) {
                buffToJava();
            }
        }
        buffToJava();
        java.append("\");\n  return out.toString();");
        renderer = CompileSourceInMemory.<Renderer>createSimpleInstance(Renderer.class, java.toString());
        System.out.println(renderer.render());
        buff=null;
        escapes=null;
        java=null;
    }
    private void buffToJava() {
        int i=0;
        for (Integer pos : escapes) {
            java.append(buff, i, pos-i); // off by 1
            Character c = buff[pos];
            if (c > 255) {
                java.append("\\u"+Integer.toHexString((int)c)); // unicode escape
            } else if (BASIC_ESCAPES[c] != null) {
                if (c == '\n') {
                    java.append("\\n\"\n      +\"");
                } else {
                    java.append('\\').append(BASIC_ESCAPES[c]);
                }
            } else {
                java.append(String.format("%3o",(int)c));
            }
            i=pos+1;
        }
        java.append(buff, i, buffI-i);
        buffI=0;
        escapes.clear();
    }
    public static void main(String[] args) throws Exception {
        new Template(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8").toCharArray());
    }
}

