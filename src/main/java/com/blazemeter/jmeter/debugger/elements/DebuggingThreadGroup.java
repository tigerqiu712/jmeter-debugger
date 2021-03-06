package com.blazemeter.jmeter.debugger.elements;

import com.blazemeter.jmeter.debugger.engine.DebuggerEngine;
import com.blazemeter.jmeter.debugger.engine.DebuggingThread;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.ListenerNotifier;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.ListedHashTree;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

public class DebuggingThreadGroup extends ThreadGroup implements OriginalLink<ThreadGroup> {
    private static final Logger log = LoggingManager.getLoggerForClass();
    private Thread osThread;
    private DebuggingThread jmeterThread;
    private final long waitTime = JMeterUtils.getPropDefault("jmeterengine.threadstop.wait", 5 * 1000);
    private boolean stopping = false;
    private ThreadGroup original;


    public DebuggingThreadGroup() {
        super();
        setDelay(0);
        setNumThreads(1);
        setRampUp(0);
        LoopController ctl = new LoopController();
        ctl.setContinueForever(true);
        ctl.setLoops(-1);
        setSamplerController(ctl);
    }

    @Override
    public void start(int groupCount, ListenerNotifier notifier, ListedHashTree threadGroupTree, StandardJMeterEngine engine) {
        JMeterContext context = JMeterContextService.getContext();
        DebuggingThread jmThread = makeThread(groupCount, notifier, threadGroupTree, engine, 0, context);
        Thread newThread = new Thread(jmThread, jmThread.getThreadName());
        if (engine instanceof DebuggerEngine) {
            DebuggerEngine dbgEngine = (DebuggerEngine) engine;
            dbgEngine.setTarget(jmThread);
            dbgEngine.setThread(newThread);

            this.jmeterThread = jmThread;
            this.osThread = newThread;
        }
        newThread.start();
    }

    private DebuggingThread makeThread(int groupCount, ListenerNotifier notifier, ListedHashTree threadGroupTree, StandardJMeterEngine engine, int i, JMeterContext context) {
        // had to copy whole method because of this line
        DebuggingThread jmeterThread = new DebuggingThread(threadGroupTree, this, notifier, context);

        boolean onErrorStopTest = getOnErrorStopTest();
        boolean onErrorStopTestNow = getOnErrorStopTestNow();
        boolean onErrorStopThread = getOnErrorStopThread();
        boolean onErrorStartNextLoop = getOnErrorStartNextLoop();
        String groupName = getName();

        jmeterThread.setThreadNum(i);
        jmeterThread.setThreadGroup(this);
        jmeterThread.setInitialContext(context);
        String threadName = groupName + " " + (groupCount) + "-" + (i + 1);
        jmeterThread.setThreadName(threadName);
        jmeterThread.setEngine(engine);
        jmeterThread.setOnErrorStopTest(onErrorStopTest);
        jmeterThread.setOnErrorStopTestNow(onErrorStopTestNow);
        jmeterThread.setOnErrorStopThread(onErrorStopThread);
        jmeterThread.setOnErrorStartNextLoop(onErrorStartNextLoop);
        return jmeterThread;
    }

    @Override
    public void waitThreadsStopped() {
        super.waitThreadsStopped();
        if (osThread != null) {
            while (osThread.isAlive()) {
                if (stopping) {
                    log.debug("Interrupting thread: " + osThread);
                    osThread.interrupt();
                }

                log.debug("Joining thread 1: " + osThread + " " + stopping + " " + osThread.isInterrupted());

                try {
                    osThread.join(waitTime);
                } catch (InterruptedException e) {
                    log.debug("Interrupted: " + e);
                    break;
                }
            }
            log.debug("Thread is done: " + osThread);
        }
    }

    @Override
    public void tellThreadsToStop() {
        stopping = true;
        super.tellThreadsToStop();
        if (jmeterThread != null) {
            log.debug("Interrupting JMeter thread: " + jmeterThread);
            jmeterThread.interrupt();
        }

        if (osThread != null) {
            log.debug("Interrupting OS thread: " + osThread);
            osThread.interrupt();
        }
    }

    @Override
    public boolean verifyThreadsStopped() {
        boolean stopped = super.verifyThreadsStopped();
        if (osThread != null) {
            if (osThread.isAlive()) {
                log.debug("Joining thread 2: " + osThread);
                try {
                    osThread.join(waitTime);
                } catch (InterruptedException e) {
                    log.debug("Interrupted: " + e);
                }
                if (osThread.isAlive()) {
                    stopped = false;
                    log.warn("Thread didn't exit: " + osThread);
                }
            }
        }
        return stopped;
    }

    @Override
    public ThreadGroup getOriginal() {
        return original;
    }

    @Override
    public void setOriginal(ThreadGroup orig) {
        original = orig;
    }
}
