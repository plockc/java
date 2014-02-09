package plock.fio;

import static java.nio.file.StandardWatchEventKinds.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;
import plock.adt.*;



// TODO: could maybe add internal handlers that deal with directory heirarchy appearing and disappearing
// TODO: executor service for handling threading a long list of notifications and exceptions from them
/** A service to watch a files and handle callbacks.  To use, create an instance of FileWatcher,
 * which underneath will instantiate a java WatchService.  
 * Any FileChangedHandlers registered with fileChangeListener gets called when the file changes.
 * createAutoUpdatingRef will always have the latest create()'d object, and
 * createAutoUpdatingProxy will pretend to be any desired interface (like java.util.Map) but
 * will always call the latest create()'d instance.
 */
public class FileWatcher implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);
    private static int id=0; // used for anonymous filewatcher naming
    private final WatchService watchService;
    private final MapOfLists<Path, FileChangedHandler> pathToHandlers; 
    private final MapOfLists<Path, AB<Factory,Handle>> pathToFactoryAndRef;

    /** handler that can be registered with fileChangeListener */
    public interface FileChangedHandler {public void handleFileChanged(Path path);}
    /** Factories are registered with createAutoUpdatingProx/Ref and when the file changes, the
     * factory is called to create the newer version of the object referred to by the Ref instance
     * passed back during registration */
    public interface Factory {public Object create(Path p);}
    /** a reference to the object created by the user's Factory.create method, where the 
     * referred object is updated anytime the path is updated */
    public interface Ref<T> extends Closeable { public T get(); }
    /** Internal wrapper for objects created from client's Factory.create method, allowing
     *  for replacement of the wrapped value, only the Ref.get() method is exposed to the user */
    private static class Handle<T> implements Ref<T> {
        Object obj;
        WatchKey key;
        public Handle(Object obj, WatchKey key) {this.obj = obj; this.key = key;}
        public synchronized T get() {return (T)obj;}
        public void finalize() {close();}
        public void close() {if (key != null) {key.cancel();}}
        public synchronized void set(Object newObj) {this.obj = newObj;}
    }

    /** creates an anonymous FileWatcher */
    public FileWatcher() {this("anonymous-"+(id++));}
    /** @param name used to name the thread handling the java WatchEvents */
    public FileWatcher(final String name) {
        WatchService newWatchService = null;
        try {
            newWatchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("failed to get a watch service for file system, will not be able to detect file changes");
        }
        watchService = newWatchService; 
        pathToHandlers = new MapOfLists<Path, FileChangedHandler>();
        pathToFactoryAndRef = new MapOfLists<Path, AB<Factory,Handle>>();
        Thread t = new Thread(new Runnable() {public void run() {
            while (true) {
                try {
                    WatchKey key = watchService.take();
                    for(WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == OVERFLOW) {continue;}
                        Path changedPath = ((Path)key.watchable()).resolve(((WatchEvent<Path>)event).context());
                        changedPath = changedPath.toAbsolutePath().normalize();
                        if (log.isDebugEnabled()) {log.debug("file changed: "+changedPath);}
                        List<FileChangedHandler> handlers;
                        synchronized (pathToHandlers) {
                            handlers = pathToHandlers.get(changedPath);
                        }
                        if (handlers != null) {
                            for (FileChangedHandler handler : handlers) {
                                handler.handleFileChanged(changedPath);
                            }
                        }
                        List<AB<Factory,Handle>> factoriesAndRefs;
                        synchronized (pathToFactoryAndRef) {
                            factoriesAndRefs = pathToFactoryAndRef.get(changedPath);
                        }
                        if (factoriesAndRefs != null) {
                            for (AB<Factory,Handle> factoryAndRef : factoriesAndRefs) {
                                try {
                                    Handle handle = factoryAndRef.b;
                                    handle.set(factoryAndRef.a.create(changedPath));
                                } catch (RuntimeException exception) {
                                    log.warn("got exception while trying to reload "+changedPath, exception);
                                } catch (Error error) {
                                    log.error("got error while trying to reload "+changedPath, error);
                                }
                            }
                        }
                    }
                    if (!key.reset()) {
                        log.warn("failed to reset watchkey, no longer watching for "+name);
                        break;
                    }
                } catch (InterruptedException ignored) {
                } catch (ClosedWatchServiceException ignored) {
                    log.debug("closed, no longer file watching for "+name);
                    return;
                }
            }
        }}, "FileWatcher "+name);
        t.setDaemon(true);
        t.start();
    }

    /** 
     * This is the same behavior as the ref but wrapped in a friendly proxy, at the cost of a factor of api call
     * performance, though that is mitigated if the API calls do processing.
     * To cancel the underlying watchkey, cast to Closeable then call close
     * @return null if factory initially returns null, else the latest non-null result from factory
     */
    public <T> T createAutoUpdatingProxy(final Path path, final Class<T> clazz, final Factory factory) {
        final Handle<T> handle = (Handle<T>)createAutoUpdatingRef(path, factory);
        if (handle.get() == null) {return null;}
        T p = (T)Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] {clazz, Closeable.class}, new InvocationHandler() {
            T lastKnown = handle.get();
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (method.getName().equals("close")) {handle.close();}
                T proxied = handle.get();
                lastKnown = proxied == null ? lastKnown : proxied;
                try {
                    return method.invoke(lastKnown, args);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("weird proxy error: "+e, e);
                }
            }
        });
        return (T)p;
    }
 
    /** 
     * Passes back a Ref that will return the object created by the passed in factory, and
     * that object will automatically be updated with the file referenced by Path changes
     * TODO: cannot deal with missing parent directory
     * TODO: provide support for reloading exception handler 
     * */
    public <T> Ref<T> createAutoUpdatingRef(final Path path, final Factory factory) {
        if (factory == null) {throw new NullPointerException("Cannot have a null factory");}
        final Path parent = path.toAbsolutePath().getParent();
        final Object created = factory.create(path);
        if (parent == null || Files.notExists(parent)) {
            log.warn("configuration file directory missing: "+parent.toAbsolutePath());
            return new Handle(created, null);
        }
        try {
            final WatchKey watchKey = parent.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            Handle handle = new Handle(created, watchKey);
            synchronized(pathToFactoryAndRef) {
                pathToFactoryAndRef.addValueToKey(path.toAbsolutePath().normalize(), AB.ab(factory, handle));
            }
            return handle;
        } catch (IOException e) {
            return new Handle(created, null);
        }
    }

    public Closeable fileChangeListener(final Path path, final FileChangedHandler handler) {
        if (handler == null) {throw new NullPointerException("Cannot have a null handler");}
        final Path parent = path.getParent();
        if (Files.notExists(parent)) {
            log.warn("configuration file directory missing: "+parent.toAbsolutePath()); return null;
        }
        try {
            synchronized(pathToHandlers) {
                pathToHandlers.addValueToKey(path.toAbsolutePath().normalize(), handler);
            }
            final WatchKey watchKey = parent.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            return new Closeable() {public void close() {watchKey.cancel();}};
        } catch (Exception e) {
            log.error("OS does not support watch service");
            return null;
        }
    }
    
    public void close() throws IOException {
        watchService.close();
    }
}
