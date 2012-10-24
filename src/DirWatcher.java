import java.io.*;

import java.text.*;

import java.util.*;
import java.util.concurrent.*;


/*
watches one dir for adding, removing and updating files, not recursive
*/
public class DirWatcher {

    public static interface EventHandler {
        public void added(File f);
        public void modified(File f);
        public void removed(File f);
    }

    /*
    a commons daemon test

    Using jsvc: There two ways to use jsvc: via a Class that implements the
    Daemon interface or via calling a Class that has the required methods.

    Directly

        Write a Class (MyClass) that implements the following methods:

            * void init(String[] arguments): Here open configuration files,
              create a trace file, create ServerSockets, Threads

            * void start(): Start the Thread, accept incoming connections

            * void stop(): Inform the Thread to terminate the run(),
              close the ServerSockets

            * void destroy(): Destroy any object created in init()

        Store it in a jarfile and use as above:

        ./jsvc -cp my.jar MyClass
    */

// ~~~~~ daemon methods -start

    // daemon prints exception on stderr
    // if no arguments or -help argument is passed prints following in syserr
    // Error: required argument is missing <dir>
    // Usage: DirWatcher <dir>
    // Service exit with a return value of 1
    //
    public void init(String[] args) {
        log(">init " + Arrays.toString(args));

        String arg = (args.length > 0) ? args[0] : null;

        if (arg == null) {
            System.err.println("Error: required argument is missing <dir>");
        }

        if ((arg == null)
                || Arrays.asList("-h", "-help", "--help").contains(arg.toLowerCase())) {
            System.err.printf("Usage: %s <dir>%n",
                    System.getProperty("progname", DirWatcher.class.getSimpleName()));
            System.exit(1);
        }

        init0(arg);
        log("<init ");
    }

    // Start the Thread, accept incoming connections
    public void start() {
        log(">start");
        start0();
        schedule();
        log("<start");
    }

    // Inform the Thread to terminate the run(), close the ServerSockets
    public void stop() {
        log(">stop");
        stop0();
        log("<stop");
    }

    // Destroy any object created in init()
    public void destroy() {
        log(">destroy");
        log("<destroy");
    }

// ~~~~~ daemon methods -end

    private File _fdir;
    private HashMap<String, Long> _entries = new HashMap<String, Long>();
    private ScheduledExecutorService _scheduler;
    private long _schedulerIntialDelay  = 10; // seconds
    private long _schedulerPeriod       = 10; // seconds
    private EventHandler _eventHandler;

    /* Daemon class must have a public no arg ctor
    */
    public DirWatcher() {
    }

    void init0(String dir) {
        File fdir = new File(dir);

        if (!fdir.isDirectory()) {
            throw new RuntimeException(dir +
                " is not a dir or permission denied");
        }

        if (!fdir.canRead()) {
            throw new RuntimeException(dir + " read permission denied");
        }

        _fdir = fdir;

        _scheduler = Executors.newScheduledThreadPool(1 /*thread*/);

        _eventHandler = new EventHandler(){
            public void added(File f){
                log("EventHandler.added: %s (%s)",
                    f,strdate(f.lastModified()));
            }
            public void modified(File f){
                log("EventHandler.modified: %s (%s)",
                    f,strdate(f.lastModified()));
            }
            public void removed(File f){
                log("EventHandler.removed: %s (%s)",
                    f,strdate(f.lastModified()));
            }
        };
    }

    void schedule() {
        final ScheduledFuture<?> handler = _scheduler.scheduleAtFixedRate(
                                                new Runnable() {
                                                    public void run() {
                                                        DirWatcher.this.watch();
                                                    }
                                                },
                                                _schedulerIntialDelay,
                                                _schedulerPeriod,
                                                TimeUnit.SECONDS
                                                );
    }

    void stop0() {
        if (_scheduler != null) {
            _scheduler.shutdown();
        }
    }

    void start0() {
        File[] files = _fdir.listFiles();
        int len = files.length;

        for (int i = 0; i < len; i++) {
            _entries.put(files[i].getName(), files[i].lastModified());
        }
    }

    void watch() {
        log(">watch");
        File[] files = _fdir.listFiles();
        int len = files.length;
        int esz = _entries.size();

        if (len < esz) {
            log("%s files has been removed", (esz - len));
        } else if (len > esz) {
            log("%s files has been added", (esz - len));
        }

        Map<String, Long> tmp_entries = (Map<String, Long>) _entries.clone();

        for (int i = 0; i < len; i++) {
            File f = files[i];
            String fname = f.getName();
            Long lastmodified = tmp_entries.remove(fname);
            long newLastModified = f.lastModified();

            if (lastmodified != null) {
                long _lastmodified = lastmodified.longValue();
                if (_lastmodified != newLastModified) {
                    log("file %s has been modified, old(%s) new(%s)",
                        f,
                        strdate(_lastmodified),
                        strdate(newLastModified)
                       );
                    _entries.put(fname, newLastModified); // update existing
                    _eventHandler.modified(f);
                }
            } else {
                log("new file %s has been added, new(%s)",
                    f,
                    strdate(newLastModified)
                   );
                _entries.put(fname, newLastModified); // add new entries
                _eventHandler.added(f);
            }
        }

        esz = tmp_entries.size();

        if (esz > 0) {
            for (Map.Entry<String, Long> e : tmp_entries.entrySet()) {
                String fname = e.getKey();
                log("file %s removed, old(%s)", fname, strdate(e.getValue()));
                _entries.remove(fname); // remove deleted entries
                _eventHandler.removed(new File(fname));
            }
        }
        log("<watch");
    }

    static final SimpleDateFormat DFMT = new SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    static String strdate(long dt){return DFMT.format(new Date(dt));}
    static String strdate(Date dt){return DFMT.format(dt);}
    static String now(){return strdate(new Date());}

    static final String LN = System.getProperty("line.separator");
    static void log(String fmt, Object... args) {
        System.out.println(now() + " " + Thread.currentThread() + " - "
            + String.format(fmt, args));
    }
    static void log(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split(LN);
        for (int i = 0; i < lines.length; i++)log(lines[i]);
    }
}
/*
Usage: jsvc [-options] class [args...]

Where options include:

    -help | --help | -?
        show this help page (implies -nodetach)
    -jvm <JVM name>
        use a specific Java Virtual Machine. Available JVMs:
            'null'
    -client
        use a client Java Virtual Machine.
    -server
        use a server Java Virtual Machine.
    -cp | -classpath <directories and zip/jar files>
        set search path for service classes and resouces
    -java-home | -home <directory>
        set the path of your JDK or JRE installation (or set
        the JAVA_HOME environment variable)
    -version
        show the current Java environment version (to check
        correctness of -home and -jvm. Implies -nodetach)
    -showversion
        show the current Java environment version (to check
        correctness of -home and -jvm) and continue execution.
    -nodetach
        don't detach from parent process and become a daemon
    -debug
        verbosely print debugging information
    -check
        only check service (implies -nodetach)
    -user <user>
        user used to run the daemon (defaults to current user)
    -verbose[:class|gc|jni]
        enable verbose output
    -outfile </full/path/to/file>
        Location for output from stdout (defaults to /dev/null)
        Use the value '&2' to simulate '1>&2'
    -errfile </full/path/to/file>
        Location for output from stderr (defaults to /dev/null)
        Use the value '&1' to simulate '2>&1'
    -pidfile </full/path/to/file>
        Location for output from the file containing the pid of jsvc
        (defaults to /var/run/jsvc.pid)
    -D<name>=<value>
        set a Java system property
    -X<option>
        set Virtual Machine specific option
    -ea[:<packagename>...|:<classname>]
    -enableassertions[:<packagename>...|:<classname>]
        enable assertions
    -da[:<packagename>...|:<classname>]
    -disableassertions[:<packagename>...|:<classname>]
        disable assertions
    -esa | -enablesystemassertions
        enable system assertions
    -dsa | -disablesystemassertions
        disable system assertions
    -agentlib:<libname>[=<options>]
        load native agent library <libname>, e.g. -agentlib:hprof
    -agentpath:<pathname>[=<options>]
        load native agent library by full pathname
    -javaagent:<jarpath>[=<options>]
        load Java programming language agent, see java.lang.instrument
    -procname <procname>
        use the specified process name
    -wait <waittime>
        wait waittime seconds for the service to start
        waittime should multiple of 10 (min=10)
    -stop
        stop the service using the file given in the -pidfile option
    -keepstdin
        does not redirect stdin to /dev/null

jsvc (Apache Commons Daemon) 1.0.10
Copyright (c) 1999-2011 Apache Software Foundation.

*/
