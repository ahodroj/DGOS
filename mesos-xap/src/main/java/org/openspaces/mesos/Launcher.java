package org.openspaces.mesos;

import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Status;
import org.openspaces.mesos.scheduler.XAPScheduler;

public class Launcher {

	public static void main(String [] args) {
		if(args.length < 2) {
			System.err.println("Usage: mesos-xap <Master URI> <LUS address>");
			System.exit(-1);
		}
		
		System.out.println("Starting XAP on Mesos with master " + args[0]);
		Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
				.setName("Grid Elastic Services Manager")
				.setUser("")
				.build();
		
		MesosSchedulerDriver schedulerDriver = new MesosSchedulerDriver(new XAPScheduler(args[1], "space"), frameworkInfo, args[0]);
		int status = schedulerDriver.run() == Status.DRIVER_STOPPED ? 0 : 1;

		schedulerDriver.stop();

		System.out.println("Finished running scheduler");
		
		System.exit(status);
	}
}

