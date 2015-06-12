package org.jcodes.profiling.cpumonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import junit.framework.TestCase;

public class CpuMonitorTest extends TestCase {
	private static Logger log = Logger.getAnonymousLogger();
	private static int monitorInterval = 2;
	private static int historyCount = 10;
	private static int processLoadThreshood = 60;
	private static int poolSize = 20;
	private static int delay = 1000;
	private static int timeout = 30;
	private static int loopCount = 1000 * 1000;


	public void testCpuMonitor() {
		CpuMonitor monitor = new CpuMonitor(buildConfig());
		monitor.startMonitor();

    	try {
    		Thread.sleep(monitorInterval + 1);
    	} catch (InterruptedException e) {
    	}

    	// run biz threads
    	runBizThreads();

    	try {
    		Thread.sleep(monitorInterval + 1);
    	} catch (InterruptedException e) {
    	}

    	monitor.stopMonitor();
	}


	private Configuration buildConfig() {
		ConfigurationImpl config = new ConfigurationImpl();
		config.setMonitorInterval(monitorInterval);
		config.setHistoryCount(historyCount);
		config.setProcessLoadThreshood(processLoadThreshood);
		config.setLockedMonitors(true);
		config.setLockedSynchronizers(true);
		config.setThreadLoadThreshood(1);
		config.setRealProcessors(-1);
		config.setThreadInfoFilterRegex("org\\.jcodes\\.cpumonitor\\.");
		return config;
	}

	private void runBizThreads() {
    	log.info("CpuMonitorTest.runBizThreads begin.");
		// run biz threads
		ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(poolSize);
        for (int i = 0; i < poolSize; i++) {
        	final int taskId = i;
        	log.info("create task.(taskId:" + taskId + ")");
        	// start one task after the given delay
            threadPool.schedule(new Runnable() {
                public void run() {
                	log.info("run task begin.(taskId:" + taskId + ")");
                	long startTime = System.currentTimeMillis();
                	while (true) {
	                    try {
	                    	int taskType = taskId % 3;
	                    	if (taskType == 1) task1();
	                    	else if (taskId % 3 == 2) task2();
	                    	else if (taskType == 0) task3();
	                    	Thread.sleep((poolSize - taskId) * 200);
	                    } catch (InterruptedException e) {
	                    }
	                    long estimatedTime = System.currentTimeMillis() - startTime;
	                    if (estimatedTime / 1000 > timeout) break;
                	}
                	log.info("run task end.(taskId:" + taskId + ")");
                }
                private void task1() {
                	for (int i = 0; i < loopCount * taskId; i++) {
                		Integer n = new Integer(i);
                		n = n + 2;
                		n = n - 2;
                	}
                }
                private void task2() {
                	for (int i = 0; i < loopCount * taskId; i++) {
                		Integer n = new Integer(i);
                		n = n * 2;
                		n = n / 2;
                	}
                }
                private void task3() {
                	for (int i = 0; i < loopCount * taskId; i++) {
                		String s = new String("i=" + i);
                		s = s + 2;
                	}
                }
            }, delay * taskId, TimeUnit.MILLISECONDS);
        }

        // wait until the timeout occurs
        boolean isAllTerminated = false;
        try {
        	log.info("wait until the timeout occurs");
            threadPool.shutdown();
            isAllTerminated = threadPool.awaitTermination(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        } finally {
        	log.info("wait until all tasks have completed.");
            // wait until all tasks have completed
            while (!isAllTerminated) {
            	try {
            		Thread.sleep(delay);
            		isAllTerminated = threadPool.isTerminated();
            	} catch (InterruptedException e) {
            	}
            }
        }
    	log.info("CpuMonitorTest.runBizThreads end.");
	}
}
