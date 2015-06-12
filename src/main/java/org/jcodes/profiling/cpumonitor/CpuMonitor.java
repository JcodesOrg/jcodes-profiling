package org.jcodes.profiling.cpumonitor;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.ObjectName;

public class CpuMonitor {
	private static Logger log = Logger.getAnonymousLogger();
	private Configuration config;
	private Thread thread;
	private boolean isIbmJvm;
	private MBeanServer server;
	private ObjectName osb;
	private int availableProcessors;
	private ThreadMXBean thb;
	private boolean isThreadTimeEnabled;
	private long systemTime;
	private long processUpTime;

	class ThreadTime {
		public long tid;
		public long sysTime;
		public long cpuTime;
		public long userTime;
	}
	Map<Long, ThreadTime> ttMap = new HashMap<Long, ThreadTime>();

	class ThreadLoad {
		public ThreadInfo thi;
		public long thlc;
		public long thlu;
	}
	class Snapshot {
		public String sid;
		public long processLoad;
		public long totalThlc;
		public long totalThlu;
		List<ThreadLoad> tlList = new ArrayList<ThreadLoad>();
		public boolean outputFinished;
	}
	List<Snapshot> history = new ArrayList<Snapshot>();

	public CpuMonitor(Configuration config) {
		this.config = config;
		init();
	}

	public void startMonitor() {
		log.info("CpuMonitor.startMonitor begin.");
		systemTime = 0;
		processUpTime = 0;
		ttMap.clear();
		history.clear();
		thread = new Thread() {
			@Override
			public void run() {
				log.info("Monitor thread begin.");
				try {
					while (true) {
						Thread.sleep(config.getMonitorInterval() * 1000);
						monitor();
					}
				} catch (InterruptedException e) {
				} catch (Exception e) {
					log.warning("Monitor failured in thread process!!(ex:" + e.getMessage() + ")");
				}
				log.info("Monitor thread end.");
			}
		};
		thread.setDaemon(true);
		thread.start();
		log.info("CpuMonitor.startMonitor end.");
	}

	public void stopMonitor() {
		log.info("CpuMonitor.stopMonitor begin.");
		thread.interrupt();
		log.info("CpuMonitor.stopMonitor end.");
	}

	private void init() {
		try {
			log.info("CpuMonitor.init begin.");

			isIbmJvm = System.getProperties().get("java.vm.name").toString().contains("IBM");
			server = ManagementFactory.getPlatformMBeanServer();
			osb = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
			availableProcessors = (Integer) server.getAttribute(osb, "AvailableProcessors");
			if ((Long) server.getAttribute(osb, "ProcessCpuTime") < 0)
				throw new RuntimeException("ProcessCpuTime is not supported!!");
			thb = ManagementFactory.getThreadMXBean();
			try {
				isThreadTimeEnabled = thb.isThreadCpuTimeEnabled();
				if (!isThreadTimeEnabled) {
					thb.setThreadCpuTimeEnabled(true);
					isThreadTimeEnabled = thb.isThreadCpuTimeEnabled();
				}
			} catch (Exception e) {
				log.warning("Failured to set ThreadTimeEnabled!!(ex:" + e.getMessage() + ")");
			}

			log.info("CpuMonitor.init end.");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void monitor() throws Exception {
		while (history.size() >= config.getHistoryCount()) history.remove(0);

		// build snapshot
		Snapshot snapshot = new Snapshot();
		history.add(snapshot);
		snapshot.sid = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss SSS").format(Calendar.getInstance().getTimeInMillis());

		long curSystemTime = System.nanoTime();
		long curProcessUpTime = (Long) server.getAttribute(osb, "ProcessCpuTime");
		// in ibm jvm, getProcessCpuTime returns value of 100ns
		if (isIbmJvm) curProcessUpTime *= 100;
		int processors = config.getRealProcessors();
		if (processors <= 0) processors = availableProcessors;
		snapshot.processLoad = 100 * (curProcessUpTime - processUpTime)
				/ (processors * (curSystemTime - systemTime));
		if (snapshot.processLoad < 0) snapshot.processLoad = 0;
		systemTime = curSystemTime;
		processUpTime = curProcessUpTime;

		Map<Long, ThreadTime> curTtMap = new HashMap<Long, ThreadTime>();
		for (ThreadInfo thi : thb.dumpAllThreads(config.isLockedMonitors(), config.isLockedSynchronizers())) {
			if (thi == null) continue;
			ThreadLoad tl = new ThreadLoad();
			snapshot.tlList.add(tl);
			tl.thi = thi;
			if (!isThreadTimeEnabled) continue;
			long curSysTime = System.nanoTime();
			long curCpuTime = thb.getThreadCpuTime(thi.getThreadId());
			long curUserTime = thb.getThreadUserTime(thi.getThreadId());
			if (curCpuTime != -1 && curUserTime != -1) {
				ThreadTime tt = ttMap.get(thi.getThreadId());
				if (tt != null) {
					long interval = (processors * (curSysTime - tt.sysTime));
					tl.thlc = 100 * (curCpuTime - tt.cpuTime) / interval;
					if (tl.thlc < 0)
						tl.thlc = 0;
					snapshot.totalThlc += tl.thlc;
					tl.thlu = 100 * (curUserTime - tt.userTime) / interval;
					if (tl.thlu < 0) tl.thlu = 0;
					snapshot.totalThlu += tl.thlu;
				}

				ThreadTime curTt = new ThreadTime();
				curTt.sysTime = curSysTime;
				curTt.cpuTime = curCpuTime;
				curTt.userTime = curUserTime;
				curTtMap.put(thi.getThreadId(), curTt);
			}
		}
		ttMap = curTtMap;

		// check value of process load
		long processLoad = 0;
		for (Snapshot his : history)
			processLoad += his.processLoad;
		processLoad = processLoad / history.size();
		if (processLoad < config.getProcessLoadThreshood()) return;

		// output snapshot info
		long thlt = config.getThreadLoadThreshood();
		StringBuilder sb = new StringBuilder();
		sb.append("Monitor result!!" + '\n');
		sb.append("------ SnapshotId[]=" + history.get(0).sid + " - "
				+ history.get(history.size() - 1).sid);
		sb.append(" average(ProcessLoad)=" + processLoad);
		sb.append('\n');
		for (Snapshot his : history) {
			if (his.outputFinished)
				continue;
			else
				his.outputFinished = true;
			sb.append("------ SnapshotId=" + his.sid + " ProcessLoad="
					+ his.processLoad + " ThreadCount=" + his.tlList.size());
			if (isThreadTimeEnabled)
				sb.append(" TotalThlc=" + his.totalThlc + " TotalThlu="
						+ his.totalThlu);
			sb.append('\n');
			for (ThreadLoad tl : his.tlList) {
				if (isThreadTimeEnabled && tl.thlc <= thlt && tl.thlu <= thlt)
					continue;
				sb.append("--- ThreadId=" + tl.thi.getThreadId()
						+ " ThreadName=" + tl.thi.getThreadName());
				if (isThreadTimeEnabled)
					sb.append(" Thlc=" + tl.thlc + " Thlu=" + tl.thlu);
				sb.append('\n');
				String trc = getThreadInfoString(tl.thi);
				String filterRegex = config.getThreadInfoFilterRegex();
				if (filterRegex == null || filterRegex.isEmpty()
						|| Pattern.compile(filterRegex).matcher(trc).find()) {
					sb.append(trc);
					sb.append('\n');
				}
			}
		}
		log.info(sb.toString());
	}

	private String getThreadInfoString(ThreadInfo ti) {
		StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\""
				+ " Id=" + ti.getThreadId() + " " + ti.getThreadState());
		if (ti.getLockName() != null) {
			sb.append(" on " + ti.getLockName());
		}
		if (ti.getLockOwnerName() != null) {
			sb.append(" owned by \"" + ti.getLockOwnerName() + "\" Id="
					+ ti.getLockOwnerId());
		}
		if (ti.isSuspended()) {
			sb.append(" (suspended)");
		}
		if (ti.isInNative()) {
			sb.append(" (in native)");
		}
		sb.append('\n');
		int i = 0;
		// for (; i < stackTrace.length && i < MAX_FRAMES; i++) {
		StackTraceElement[] stackTrace = ti.getStackTrace();
		for (; i < stackTrace.length; i++) {
			StackTraceElement ste = stackTrace[i];
			sb.append("\tat " + ste.toString());
			sb.append('\n');
			if (i == 0 && ti.getLockInfo() != null) {
				Thread.State ts = ti.getThreadState();
				switch (ts) {
				case BLOCKED:
					sb.append("\t-  blocked on " + ti.getLockInfo());
					sb.append('\n');
					break;
				case WAITING:
					sb.append("\t-  waiting on " + ti.getLockInfo());
					sb.append('\n');
					break;
				case TIMED_WAITING:
					sb.append("\t-  waiting on " + ti.getLockInfo());
					sb.append('\n');
					break;
				default:
				}
			}

			MonitorInfo[] lockedMonitors = ti.getLockedMonitors();
			for (MonitorInfo mi : lockedMonitors) {
				if (mi.getLockedStackDepth() == i) {
					sb.append("\t-  locked " + mi);
					sb.append('\n');
				}
			}
		}
		if (i < stackTrace.length) {
			sb.append("\t...");
			sb.append('\n');
		}

		LockInfo[] locks = ti.getLockedSynchronizers();
		if (locks.length > 0) {
			sb.append("\n\tNumber of locked synchronizers = " + locks.length);
			sb.append('\n');
			for (LockInfo li : locks) {
				sb.append("\t- " + li);
				sb.append('\n');
			}
		}
		sb.append('\n');
		return sb.toString();
	}

}
