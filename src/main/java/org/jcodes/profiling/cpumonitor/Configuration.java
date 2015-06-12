package org.jcodes.profiling.cpumonitor;

public interface Configuration {

	public int getMonitorInterval();

	public int getHistoryCount();

	public int getProcessLoadThreshood();

	public boolean isLockedMonitors();

	public boolean isLockedSynchronizers();

	public long getThreadLoadThreshood();

	public int getRealProcessors();

	public String getThreadInfoFilterRegex();

}
