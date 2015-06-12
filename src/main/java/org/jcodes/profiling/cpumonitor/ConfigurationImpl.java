package org.jcodes.profiling.cpumonitor;

public class ConfigurationImpl implements Configuration {

	private int monitorInterval;
	private int historyCount;
	private int processLoadThreshood;
	private boolean lockedMonitors;
	private boolean lockedSynchronizers;
	private long threadLoadThreshood;
	private int realProcessors;
	private String threadInfoFilterRegex;

	public int getMonitorInterval() {
		return monitorInterval;
	}

	public void setMonitorInterval(int monitorInterval) {
		this.monitorInterval = monitorInterval;
	}

	public int getHistoryCount() {
		return historyCount;
	}

	public void setHistoryCount(int historyCount) {
		this.historyCount = historyCount;
	}

	public int getProcessLoadThreshood() {
		return processLoadThreshood;
	}

	public void setProcessLoadThreshood(int processLoadThreshood) {
		this.processLoadThreshood = processLoadThreshood;
	}

	public boolean isLockedMonitors() {
		return lockedMonitors;
	}

	public void setLockedMonitors(boolean lockedMonitors) {
		this.lockedMonitors = lockedMonitors;
	}

	public boolean isLockedSynchronizers() {
		return lockedSynchronizers;
	}

	public void setLockedSynchronizers(boolean lockedSynchronizers) {
		this.lockedSynchronizers = lockedSynchronizers;
	}

	public long getThreadLoadThreshood() {
		return threadLoadThreshood;
	}

	public void setThreadLoadThreshood(long threadLoadThreshood) {
		this.threadLoadThreshood = threadLoadThreshood;
	}

	public int getRealProcessors() {
		return realProcessors;
	}

	public void setRealProcessors(int realProcessors) {
		this.realProcessors = realProcessors;
	}

	public String getThreadInfoFilterRegex() {
		return threadInfoFilterRegex;
	}

	public void setThreadInfoFilterRegex(String threadInfoFilterRegex) {
		this.threadInfoFilterRegex = threadInfoFilterRegex;
	}

}
