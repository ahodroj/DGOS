package org.openspaces.mesos.executor;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.SlaveInfo;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.openspaces.mesos.scheduler.XAPScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XAPRebalancingExecutor implements Executor {

	private static Logger log = LoggerFactory.getLogger(XAPRebalancingExecutor.class);

	private String locators;
	private String puName;
	
	public XAPRebalancingExecutor(String locators, String puName) {
		this.locators = locators;
		this.puName = puName;
	}
	
	public void registered(ExecutorDriver driver, ExecutorInfo executorInfo, FrameworkInfo frameworkInfo, SlaveInfo slaveInfo) {
		log.info("Registered an executor on slave " + slaveInfo.getHostname());
		
	}
	
	public void reregistered(ExecutorDriver driver, SlaveInfo slaveInfo) {
		log.info("Re-Registered an executor on slave " + slaveInfo.getHostname());
	}
	
	public void disconnected(ExecutorDriver driver) {
		log.info("Disconnected the XAP rebalancing executor");
		
	}
	
	public void launchTask(final ExecutorDriver driver, final TaskInfo taskInfo) {
		log.info("Launching task " + taskInfo.getTaskId().getValue());
		
		
		Thread thread = new Thread() {
			public void run() {
				
				Protos.TaskStatus status = Protos.TaskStatus.newBuilder()
						.setTaskId(taskInfo.getTaskId())
						.setState(Protos.TaskState.TASK_RUNNING)
						.build();
				
				driver.sendStatusUpdate(status);
				log.info("Running task " + taskInfo.getTaskId().getValue());
				
				GridRebalancingAgent agent = new GridRebalancingAgent(locators, puName);
				Protos.TaskStatus finalStatus = null;
				try {
					agent.rebalance();
					finalStatus = Protos.TaskStatus.newBuilder()
							.setTaskId(taskInfo.getTaskId())
							.setState(Protos.TaskState.TASK_FINISHED)
							.build();
					driver.sendStatusUpdate(finalStatus);
				} catch (Exception e) {
					log.error("Exception while running grid rebalancer " + e);
					finalStatus = Protos.TaskStatus.newBuilder()
							.setTaskId(taskInfo.getTaskId())
							.setState(Protos.TaskState.TASK_FAILED)
							.build();
					driver.sendStatusUpdate(finalStatus);
				}
			}
		};
		
		thread.start();
		
	}

	public void error(ExecutorDriver driver, String message) {
		// TODO Auto-generated method stub
		
	}

	public void frameworkMessage(ExecutorDriver driver, byte[] data) {
		// TODO Auto-generated method stub
		
	}

	public void killTask(ExecutorDriver driver, TaskID taskId) {
		// TODO Auto-generated method stub
		
	}

	public void shutdown(ExecutorDriver driver) {
		// TODO Auto-generated method stub
		
	}

	
}
