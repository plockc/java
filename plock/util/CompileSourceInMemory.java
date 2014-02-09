package plock.util;

import java.lang.reflect.*;
import java.net.URI;
import java.util.Arrays;
import java.io.*;

import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;

// TODO: want hook for getting back classname/class bytes/method or something so can cache
public class CompileSourceInMemory {
  static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static void main(String args[]) throws Exception {
    runJavaFragment("System.out.println(\"slow\");\nSystem.out.println(\"super\");");
    runJavaFragment("System.out.println(\"fast\");");
  }
  // TODO: this seems a little awkward, consider future of supporting methods, instance vars, etc.
  public static Object runJavaFragment(final String code) throws Exception {
      runJavaMethod("execute", "public static void execute() {\n"+code+"\n}");
      return null;
  }
  public static Object runJavaMethod(final String methodName, final String code) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final String name = "TransientClassCompiledInMemory";
    //final String name = "HelloWorld"+Math.abs(new java.util.Random().nextInt());
    URI uri = URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension);
    SimpleJavaFileObject source = new SimpleJavaFileObject(uri, Kind.SOURCE) {
      public CharSequence getCharContent(boolean ignoreEncodingErrors) { 
        return "public class "+name+" {\n"+code+"\n}";
      }
    };
    Iterable<? extends JavaFileObject> sources = Arrays.asList(source);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    JavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null); 
    final ByteArrayOutputStream classBytesStream = new ByteArrayOutputStream();
    JavaFileManager fileManager = new ForwardingJavaFileManager<JavaFileManager>(standardFileManager) {
      public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind,
              FileObject sibling) throws IOException {
            URI classUri = URI.create("string:///" + name.replace('.', '/') + kind.extension);
            return new SimpleJavaFileObject(classUri, kind) {
                public OutputStream openOutputStream() throws IOException {
                    return classBytesStream;
                } 
            };
      }
    };
    CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, sources);
    boolean success = task.call();
    if (success) {
	ClassLoader transientClassLoader = new ClassLoader() {
	    public Class<?> findClass(String name) throws ClassNotFoundException {
	      byte[] classBytes = classBytesStream.toByteArray();
	      return defineClass(name, classBytes, 0, classBytes.length); 
	    }
	};
        Class<?> dynamicClass = Class.forName(name, true, transientClassLoader);
        Constructor<?> constructor = dynamicClass.getConstructor(new Class[] {});
        Object instance = constructor.newInstance(new Object[] {});
        return dynamicClass.getMethod(methodName, new Class<?>[] {}).invoke(instance, new Object[] { });
    } else {
	    for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
	      System.out.println(diagnostic.getCode());
	      System.out.println(diagnostic.getKind());
	      System.out.println(diagnostic.getPosition());
	      System.out.println(diagnostic.getStartPosition());
	      System.out.println(diagnostic.getEndPosition());
	      System.out.println(diagnostic.getSource());
	      System.out.println(diagnostic.getMessage(null));
	    }
      return "failed";
    }
  }
}

