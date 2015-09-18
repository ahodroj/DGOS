package org.openspaces.mesos.scheduler;

import java.util.List;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Collections;


public class XAPScheduler implements Scheduler {

	private static Logger log = LoggerFactory.getLogger(XAPScheduler.class);
	private String locators;
	private String puName;
	private int taskCount;
	
	public XAPScheduler(String locators, String puName) {
		this.locators = locators;
		this.puName = puName;
	}
	
	public void disconnected(SchedulerDriver schedulerDriver) {
		log.info("XAP Scheduler disconnected");
	}
	
	public void error(SchedulerDriver schedulerDriver, String message) {
		log.info("XAP Scheduler error: " + message);
	}
	
	public void executorLost(SchedulerDriver schedulerDriver, ExecutorID executorId, SlaveID slaveId, int i) {
		log.info("XAP Scheduler, executor " + executorId + " - Slave: " + slaveId + " was lost");
		
	}
	
	public void frameworkMessage(SchedulerDriver schedulerDriver, ExecutorID executorId, SlaveID slaveId, byte[] bytes) {
		
	}

	public void offerRescinded(SchedulerDriver schedulerDriver, OfferID offerId) {
		
	}

	public void registered(SchedulerDriver schedulerDriver, FrameworkID frameworkId, MasterInfo masterInfo) {
		log.info("Registered scheduler with mesos cluster");
		
	}

	public void reregistered(SchedulerDriver schedulerDriver, MasterInfo masterInfo) {
		log.info("Re-registered scheduler with mesos cluster");
	}
	
	public void resourceOffers(SchedulerDriver schedulerDriver, List<Offer> offers) {
		log.info("Resource offers--");
		for(Offer offer : offers) {
			log.info("Looking into offer " + offer.getId());
			TaskID taskId = TaskID.newBuilder().setValue(String.valueOf(taskCount++)).build();
			log.info("Launching task " + taskId.getValue() + " on slave " + offer.getSlaveId().getValue());
			ExecutorInfo executor = ExecutorInfo.newBuilder()
					.setExecutorId(ExecutorID.newBuilder().setValue(String.valueOf(taskCount)))
					.setCommand(createCommand(offer))
					.setName("Grid Rebalancing Agent task")
			.build();
			
			TaskInfo taskInfo = TaskInfo.newBuilder()
                    .setName("RebalancingTask-" + taskId.getValue())
                    .setTaskId(taskId)
                    .setExecutor(ExecutorInfo.newBuilder(executor))
                    .addResources(Resource.newBuilder()
                            .setName("cpus")
                            .setType(Value.Type.SCALAR)
                            .setScalar(Value.Scalar.newBuilder()
                                    .setValue(1)))
                    .addResources(Resource.newBuilder()
                            .setName("mem")
                            .setType(Protos.Value.Type.SCALAR)
                            .setScalar(Protos.Value.Scalar.newBuilder()
                                    .setValue(128)))
                    .setSlaveId(offer.getSlaveId())
                    .build();
			schedulerDriver.launchTasks(Collections.singletonList(offer.getId()), Collections.singletonList(taskInfo));
		}
		
	}
	
	private CommandInfo createCommand(Offer offer) {
		String attrList = "";
		for(Attribute attr : offer.getAttributesList()) {
			attrList += attr.getName() + "," + attr.getScalar() + "|";
		}
		return CommandInfo.newBuilder().
					setValue("sudo echo \"" + attrList + "\" > /gigaspaces/offer_" + offer.getId().getValue())
			   .build();
	}
	
	public void slaveLost(SchedulerDriver schedulerDriver, SlaveID slaveId) {
		// TODO: Rebalance here
		
	}

	public void statusUpdate(SchedulerDriver schedulerDriver, TaskStatus status) {
		
	}

}
