package plock.util;

import java.lang.reflect.*;
import java.net.URI;
import java.util.Arrays;
import java.io.*;
import java.nio.file.*;

import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;

/** on a 6 year old laptop, it takes less than 100ms to compile a trivially small class */
public class CompileSourceInMemory {
  static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static void main(String args[]) throws Exception {
      if (args.length == 1) {
          runJavaFragment(new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8"));
      } else {
        runJavaFragment("System.out.println(\"try passing in a file as the first argument\");");
      }
  }
  public static void runJavaFragment(final String code) throws Exception {
        Runnable runnable = createSimpleInstance(Runnable.class, code);
        if (runnable == null) {throw new IllegalArgumentException("failed to compile source");}
        runnable.run();
  }
  public static <T> T createSimpleInstance(Class<T> interfaceClass, String code) throws Exception {
        Class<T> clazz = createSimpleImpl(interfaceClass, code);
        if (clazz == null) {return null;}
        Constructor<?> constructor = clazz.getConstructor(new Class[] {});
        return (T)constructor.newInstance(new Object[] {});
  }
  private static <T> String getPackageName(Class<T> interfaceClass) {
      String packageName = interfaceClass.getPackage().getName();
      return packageName.equals("java.lang") ? "" : packageName;
  }

  public static <T> Class<T> createSimpleImpl(Class<T> interfaceClass, String codeFragment) throws Exception {
      StringBuilder className = new StringBuilder();
      String packageName = getPackageName(interfaceClass);
      if (packageName.length() > 0) {
          className.append(interfaceClass.getPackage().getName()).append('.');
      }
      className.append("Dynamic"+interfaceClass.getSimpleName()+"Impl");
      return createClass(className.toString(), createSimpleImplCode(interfaceClass, codeFragment));
  }
  public static <T> String createSimpleImplCode(Class<T> interfaceClass, String codeFragment) {
      String interfaceName = interfaceClass.getSimpleName();
      Method[] methods = interfaceClass.getMethods();
      if (!interfaceClass.isInterface() || methods.length != 1) {
          throw new IllegalArgumentException("must use an interface with only a single method");
      }
      Method method = methods[0];
      String name = method.getName();
      StringBuilder classCode = new StringBuilder();
      String packageName = getPackageName(interfaceClass);
      if (packageName.length() > 0) {
          classCode.append("package "+interfaceClass.getPackage().getName()+";\n");
      }
      classCode.append("public class Dynamic"+interfaceName+"Impl implements ");
      classCode.append(interfaceClass.getName().replace('$','.')+" {\n");
      classCode.append("public "+method.getReturnType().getName()+" "+method.getName()+"(");
      int argNum=0;
      for (Class argType : method.getParameterTypes()) {
          classCode.append(argType.getName()).append(" ").append("arg"+argNum++);
      }
      classCode.append(") {\n");
      classCode.append(codeFragment);
      classCode.append("}\n}");
      return classCode.toString();
  }
  public static <T> Class<T> createClass(String fullClassName, final String classCode) throws Exception {
	long start = System.currentTimeMillis();
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    URI uri = URI.create("string:///" + fullClassName.replace('.','/') + Kind.SOURCE.extension);
    SimpleJavaFileObject source = new SimpleJavaFileObject(uri, Kind.SOURCE) {
      public CharSequence getCharContent(boolean ignoreEncodingErrors) { return classCode; } 
    };
    Iterable<? extends JavaFileObject> sources = Arrays.asList(source);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    JavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null); 
    final ByteArrayOutputStream classBytesStream = new ByteArrayOutputStream();
    JavaFileManager fileManager = new ForwardingJavaFileManager<JavaFileManager>(standardFileManager) {
      public JavaFileObject getJavaFileForOutput(Location location, String fullClassName, Kind kind,
              FileObject sibling) throws IOException {
            URI classUri = URI.create("string:///" + fullClassName.replace('.', '/') + kind.extension);
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
        ClassLoader transientClassLoader = new ClassLoader(CompileSourceInMemory.class.getClassLoader()) {
            public Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] classBytes = classBytesStream.toByteArray();
                return defineClass(name, classBytes, 0, classBytes.length); 
            }
        };
        float elapsed = ((System.currentTimeMillis()-start)/1000.0f);
        System.out.println("compiled "+fullClassName+" in "+elapsed+" seconds");
        return (Class<T>)transientClassLoader.loadClass(fullClassName);
    } else {
        System.out.println(classCode);
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            //System.out.println(diagnostic.getCode());
            //System.out.println(diagnostic.getKind());
            //System.out.println(diagnostic.getStartPosition());
            //System.out.println(diagnostic.getEndPosition());
            //System.out.println(diagnostic.getSource());
            System.out.println(diagnostic.getMessage(null));
        }
        return null;
    }
  }
}

