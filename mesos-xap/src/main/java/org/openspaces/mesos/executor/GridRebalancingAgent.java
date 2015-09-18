package org.openspaces.mesos.executor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.openspaces.admin.Admin;
import org.openspaces.admin.AdminFactory;
import org.openspaces.admin.gsc.GridServiceContainer;
import org.openspaces.admin.gsc.GridServiceContainers;
import org.openspaces.admin.machine.Machine;
import org.openspaces.admin.machine.Machines;
import org.openspaces.admin.pu.ProcessingUnit;
import org.openspaces.admin.pu.ProcessingUnitInstance;
import org.openspaces.admin.space.SpaceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gigaspaces.cluster.activeelection.SpaceMode;
import com.j_spaces.core.IJSpace;
import com.j_spaces.core.admin.IRemoteJSpaceAdmin;
import com.j_spaces.core.admin.SpaceRuntimeInfo;


public class GridRebalancingAgent {

	public GridRebalancingAgent (String locators, String processingUnitName)
	{
		this.locators=locators;
		this.processingUnitName=processingUnitName;
	}
	
	private static Logger log = LoggerFactory.getLogger(GridRebalancingAgent.class);

    private Admin admin;
    private Random random;
    private String processingUnitName;
    private ProcessingUnit pu;
    static long sleepIntervalMillis = 2000L;
    static int RELOCATION_WIGHT = 100;
    private String locators;
    static boolean rebalancePrimaries= true;
    
    
    public static void usage() {
    	
    	System.out.println("Usage:  RebalancingAgent <locators> <PU-name> [ <sleep interval-ms (default=2000ms)> <rebalance primaries (default=true)>");
    	System.exit(-1);	
    }
    
	public static void main(String[] args) throws Exception{
    	System.out.println("Args Length " + Integer.toString(args.length));

		if (args.length<2) {
			usage();
			return;
		}
		
		String locators=args[0];
		String processingUnitName=args[1];

		if (args.length>2)
			sleepIntervalMillis = Integer.valueOf(args[2]).longValue();

		if (args.length>3)
			rebalancePrimaries = Boolean.valueOf(args[3]).booleanValue();
		
		GridRebalancingAgent p = new GridRebalancingAgent(locators,processingUnitName);
		p.rebalance();
		System.exit(0);
	}


    public void rebalance() throws Exception{
        admin = new AdminFactory().addLocators(locators).createAdmin();
        //executorService = Executors.newScheduledThreadPool(1);
        random = new Random();
        log.info("Looking for " + processingUnitName + " ...");

        pu = admin.getProcessingUnits().waitFor(processingUnitName, 30, TimeUnit.SECONDS);
        if (pu==null)
        {
            log.info("Can't find " + processingUnitName + " PU!");
            return;
        }
        
        log.info("Found PU " + pu.getName());
        pu.waitForSpace(30,TimeUnit.SECONDS);
        
        log.info(pu.getName() + "--Number of partitions:      " + pu.getSpace().getNumberOfInstances());
        log.info(pu.getName() + "--Number of backup/primary:  " + pu.getSpace().getNumberOfBackups());
        log.info(pu.getName() + "--Number of instances:       " + pu.getSpace().getTotalNumberOfInstances());
                
        if (pu.getSpace().getNumberOfBackups() < 1) {
            log.error("The processing unit is not partitioned-sync2back or has no backups, the agent will not work on it");
            throw new IllegalArgumentException("The processing unit is not partitioned-sync2back or has no backups, the agent will not work on it");
        }

        if (rebalancePrimaries)
        	balancePrimariesOnAllHosts();
        else
        	putAllPrimaryOnOneHost ();
        
        System.exit(0);
    }


    private SortedSet<GridServiceContainer> getGscsSortedByNumInstances() throws Exception{

        SortedSet<GridServiceContainer> gscs = new TreeSet<GridServiceContainer>
        (
        		new Comparator<GridServiceContainer>() 
        		{
		            public int compare(GridServiceContainer gsc1, GridServiceContainer gsc2) {
		            	
		            	int machine1TotalInstanceCount = gsc1.getMachine().getProcessingUnitInstances().length;
		            	int machine2TotalInstanceCount = gsc2.getMachine().getProcessingUnitInstances().length;
		            	
		                int puDiff = ((RELOCATION_WIGHT * machine2TotalInstanceCount) + gsc2.getProcessingUnitInstances().length) - 
		                	((RELOCATION_WIGHT * machine1TotalInstanceCount) + gsc1.getProcessingUnitInstances().length);
		                
		                if (puDiff != 0) {
		                    return puDiff;
		                }
		                
		                try
		                {
		                	puDiff = getNumBackups(gsc2) - getNumBackups(gsc1);
		                }
		                catch (Exception e)
		                {
		                	log.info(">>>>>>>> Problem with getGscsSortedByNumInstances");
		                	e.printStackTrace();
		                }
		                if (puDiff != 0) {
		                    return puDiff;
		                }
                return random.nextBoolean() ? -1 : 1;
            }
        });
        

        int i = 1;
    	//log.info(">>>>>>>> Available Containers:");
    	//log.info(">>>>>>>> ---------------------:");
        for (GridServiceContainer gridServiceContainer : admin.getGridServiceContainers()) {
        	//log.info(">>>>>>>> #" +  i + " ID:" + gridServiceContainer.getUid() + " HOST:" + gridServiceContainer.getMachine().getHostName()+ " IP:" + gridServiceContainer.getMachine().getHostAddress());
            gscs.add(gridServiceContainer);
            i++;
        }

        return gscs;
    }

    private String printGsc(GridServiceContainer gsc) {
        StringBuilder builder = new StringBuilder(30);
        builder.append("[GSC:host=").append(gsc.getMachine().getHostName())
                .append(",pid=").append(gsc.getVirtualMachine().getDetails().getPid())
                .append("]");
        return builder.toString();
    }

    
    
    private String printInstance(ProcessingUnitInstance instance) throws Exception{
        StringBuilder builder = new StringBuilder(16);
        builder.append("[").append(instance.getInstanceId()).append(",").append(instance.getBackupId() + 1);
        SpaceInstance spaceInstance = instance.getSpaceInstance();
        if (spaceInstance != null) {
            builder.append(",").append(getSpaceMode(spaceInstance));
        }
        builder.append("]");
        return builder.toString();
    }

    String getInstanceID (ProcessingUnitInstance instance)
    {
    	return instance.getInstanceId()+ "_" +(instance.getBackupId() + 1);
    }
    
    private String relocateSingleInstance(GridServiceContainer from , GridServiceContainer to,HashSet<String> relocatedInstances ) throws Exception{
        final ProcessingUnitInstance instanceToRelocate = getInstanceToRelocate(pu, from,relocatedInstances);
        
        String instanceID = getInstanceID (instanceToRelocate);
        
        if (relocatedInstances.contains(instanceID))
        {
    		log.info(" >>>>>>> " + instanceID  + " already been relocated");
        	return "-1";
        }
        
        log.info("  Moving instance " + printInstance(instanceToRelocate) + " from " + printGsc(from) + " to " +  printGsc(to));
        ProcessingUnitInstance relocatedInstance = instanceToRelocate.relocateAndWait(to);        	
                
        log.info("  Done Moving instance " + printInstance(relocatedInstance) + ", hosting GSC is " +
                printGsc(relocatedInstance.getGridServiceContainer()));
        
        return getInstanceID (relocatedInstance);
    }

    private ProcessingUnitInstance getInstanceToRelocate(ProcessingUnit pu, 
    		GridServiceContainer from , 
    			HashSet<String> movedInstances) throws Exception{
        for (ProcessingUnitInstance processingUnitInstance : from.getProcessingUnitInstances(pu.getName())) {
        	// try to get instance that was not relocated 
        	String InstanceID = getInstanceID(processingUnitInstance );
        	
        	if (movedInstances.contains(InstanceID))
        		continue;
        	
            if (isBackup(processingUnitInstance)) {
                return processingUnitInstance;
            }
        }
        
        // No backups to relocate - relocate primary
        for (ProcessingUnitInstance processingUnitInstance : from.getProcessingUnitInstances(pu.getName())) {        	        	
            if (isPrimary(processingUnitInstance)) {
                return processingUnitInstance;
            }
        }
        
        return from.getProcessingUnitInstances(pu.getName())[0];
    }

   

    ProcessingUnitInstance[] getInstances() throws Exception
    {
		int spaceCount = pu.getTotalNumberOfInstances();
		int retryCount = 0;
		ProcessingUnitInstance puInstances[];
		
		while (true)
		{
			puInstances = pu.getInstances();
			retryCount ++;
			if (puInstances.length == spaceCount)
			{
				break;
			}
			
			if (retryCount == 20)
			{
				log.info("--->>> Can't get full list of instances!");
				break;
			}
			
			log.info("--->>> getInstances() retrying..." + Integer.toString(puInstances.length));			
			Thread.sleep(2000);
		}
		return puInstances;
    }


    int getObjectCount(SpaceInstance space) throws Exception
    {
    	int objectCount =0;
    	IJSpace ijsapce = space.getGigaSpace().getSpace();
    	IRemoteJSpaceAdmin spaceAdmin = (IRemoteJSpaceAdmin)ijsapce.getAdmin();
    	 
    	SpaceRuntimeInfo rtInfo = spaceAdmin.getRuntimeInfo();
    	Iterator<Integer> iter = rtInfo.m_NumOFEntries.iterator();
    	while(iter.hasNext())
    	{
    		objectCount = objectCount + iter.next().intValue();
    	}
    	return objectCount ;
    }




    private int getNumPrimaries(GridServiceContainer gsc) throws Exception{
        int numPrimaries = 0;
        ProcessingUnitInstance[] instances = gsc.getProcessingUnitInstances(pu.getName());
        for (ProcessingUnitInstance instance : instances) {
            if (isPrimary(instance)) {
                numPrimaries++;
            }
        }
        return numPrimaries;
    }

    private int getNumBackups(GridServiceContainer gsc2) throws Exception{
        int numBackups = 0;
        ProcessingUnitInstance[] instances = gsc2.getProcessingUnitInstances(pu.getName());
        for (ProcessingUnitInstance instance : instances) {
            if (isBackup(instance)) {
                numBackups++;
            }
        }
        return numBackups;
    }

    private boolean isPrimary(ProcessingUnitInstance instance) throws Exception{
    	return isMode(instance , SpaceMode.PRIMARY);
    }
    
    private boolean isMode(ProcessingUnitInstance instance , SpaceMode requestedMode) throws Exception{
        SpaceInstance spaceInstance = null;
        int retryCount=0;
        
		while (spaceInstance == null )
		{
	        spaceInstance = instance.getSpaceInstance();
	        if (spaceInstance == null)
	        {
	        	Thread.sleep(1000);
	        }
	        retryCount++;
	        if (retryCount ==10)
	        {
	        	return false;
	        }
		}
		
        retryCount=0;
		SpaceMode mode = getSpaceMode(spaceInstance);
		
		return mode.equals(requestedMode);
    }

    private SpaceMode getSpaceMode(SpaceInstance spaceInstance) throws Exception
    {
        int retryCount=0;
		SpaceMode mode = spaceInstance.getMode();
		while (mode == null )
		{
			mode = spaceInstance.getMode();
	        if (mode == null)
	        {
	        	Thread.sleep(1000);
	        }
	        retryCount++;
	        if (retryCount ==10)
	        {
	        	return mode;
	        }
		}

        retryCount=0;
		// try again in case status is NONE
		while (mode.equals(SpaceMode.NONE))
		{
			mode = spaceInstance.getMode();
	        if (!mode.equals(SpaceMode.NONE))
	        {
	        	break;
	        }
	        else
	        {
	        	Thread.sleep(1000);
	        }
	        retryCount++;
	        if (retryCount ==10)
	        {
	        	return mode ;
	        }
		}
		return mode ;
    }
    
    private boolean isBackup(ProcessingUnitInstance instance) throws Exception{
    	return isMode(instance, SpaceMode.BACKUP);
    }

    public void setProcessingUnitName(String processingUnitName) {
        this.processingUnitName = processingUnitName;
    }


    public void setSleepIntervalSecs(long sleepIntervalSecs) {
        this.sleepIntervalMillis = sleepIntervalSecs * 1000;
    }

    public void setTimeToWaitForPuSecs(long timeToWaitForPuSecs) {
    }

    
    int getAmountOfPrimary (String machineAddress) throws Exception
    {
    	Machines machines= admin.getMachines();
    	Iterator<Machine> iter = machines.iterator();
		int primaryCount = 0;
    	while (iter.hasNext())
    	{
    		Machine machine = iter.next();
    		if (machine.getHostAddress().equals(machineAddress))
    		{
    			GridServiceContainers gscs = machine.getGridServiceContainers();
    			Iterator<GridServiceContainer>  gscIter = gscs.iterator();
	    		while (gscIter.hasNext())
	    		{
	    			primaryCount = primaryCount + getNumPrimaries(gscIter.next());
	    		}
	    		break;
    		}
    	}
    	return primaryCount;
    }
    
    
    void putAllPrimaryOnOneHost () throws Exception
    {
    	log.info(" -------- putAllPrimaryOnOneHost started -------- ");
    	Hashtable<String,ArrayList<SpaceInstance>> spacesPerHostList = getSpacesOnHosts(null);
    	if  (spacesPerHostList.size()==1)
    		return;

    	String targetHost = spacesPerHostList.keys().nextElement();
    	
    	log.info("Moving all primary to:"+targetHost);
    	
		ProcessingUnitInstance puInstances[]=getInstances();
		HashSet<Integer> primaryInstancesRestarted= new HashSet<Integer>();

		for (int i=0;i<puInstances.length;i++)
		{
			if (puInstances[i].getMachine().getHostAddress().equals(targetHost))
    			continue;
			
			if (primaryInstancesRestarted.contains(puInstances[i].getInstanceId()))
    			continue;
				
			if (isPrimary(puInstances[i]))
			{
				log.info("restart ID:"+puInstances[i].getInstanceId());
				puInstances[i].restartAndWait(5000, TimeUnit.MILLISECONDS);
				primaryInstancesRestarted.add(puInstances[i].getInstanceId());
				log.info("Done restarting instance " + printInstance(puInstances[i]));
			}
			
		}    	
    }
    

    
    private Hashtable<String,ArrayList<SpaceInstance>> getSpacesOnHosts(String msg) throws Exception
	{
		Hashtable<String,ArrayList<SpaceInstance>> spaceListPerHost = new Hashtable<String,ArrayList<SpaceInstance>> ();
		ProcessingUnitInstance puInstances[]=getInstances();		
		for (int i=0;i<puInstances.length;i++ )
		{
			
			String hostAddress = puInstances[i].getMachine().getHostAddress();

			ArrayList<SpaceInstance> spaces = null;
			if (!spaceListPerHost.containsKey(hostAddress))
			{
				spaces = new ArrayList<SpaceInstance>();
			}
			else
			{
				spaces=spaceListPerHost.get(hostAddress);
			}
			
			while (puInstances[i].getSpaceInstance() == null)
			{
				Thread.sleep(1000);
			}
			
			spaces.add(puInstances[i].getSpaceInstance());
			spaceListPerHost.put(hostAddress , spaces);
		}

		if (msg != null){
			log.info("");
			log.info("===========================================================");			
			log.info(msg + " PU:"+  pu.getName()+ " running on the following Hosts");
			log.info("===========================================================");
		}
		
		Enumeration<String> keys = spaceListPerHost.keys();
		while (keys.hasMoreElements())
		{
			String host = keys.nextElement();
			
			if (msg != null) {
				log.info("Host:"+host);
				log.info("-------------------------");
			}
			
			ArrayList<SpaceInstance> spaces = spaceListPerHost.get(host);
			Iterator<SpaceInstance> spaceIter = spaces.iterator();
			while (spaceIter.hasNext())
			{
				SpaceInstance space = spaceIter.next();
				if (space != null)
				{
					SpaceMode spaceMode = getSpaceMode(space);
					if (msg != null) {
						log.info("  ID:" +space.getInstanceId() + " " +spaceMode + "\tInstance Count:" + getObjectCount(space));
					}
				}	
			}
		}
		
		if (msg != null){
			log.info("===========================================================");
			log.info("");			
		}

		return spaceListPerHost ;
	}
    
    private void rebalanceInstanceCount() throws Exception{
    	
		log.info("  ---- rebalanceInstanceCount STARTED ----");    	
        SortedSet<GridServiceContainer> gscs = getGscsSortedByNumInstances();
        GridServiceContainer firstGsc = gscs.first(); // This one got the most amount of instances
        GridServiceContainer lastGsc = gscs.last();   // This one got the least amount of instances
        
        int retryCount=0;
        HashSet<String> relocatedInstances = new HashSet<String> (); 
        
//        while (firstGsc.getProcessingUnitInstances(puName).length - lastGsc.getProcessingUnitInstances(puName).length > 1) {
        
        /*
         * we have few situations we need to support
         * - more instances than GSCS - stop rebalancing when first and last got same relocation value 
         * - more GSCS than instances - stop rebalancing when first have one instance
         */
        
        while (true) {

//     	log.info("Before move cycle - First GSC got:" + firstGsc.getProcessingUnitInstances(puName).length+ " instances" + " Last GSC got:" + lastGsc.getProcessingUnitInstances(puName).length+ " instances" );

        	// same amount of instances on all machines
            if (firstGsc.getMachine().getProcessingUnitInstances().length == lastGsc.getMachine().getProcessingUnitInstances().length ) 
            {
                log.info("Can't rebalanceInstanceCount anymore! - All machines have same amount of instances!");
            	break;            	
            }

        	// delta between first and last is one 
            if ((firstGsc.getMachine().getProcessingUnitInstances().length-1) == lastGsc.getMachine().getProcessingUnitInstances().length ) 
            {
                log.info("Can't rebalanceInstanceCount anymore! - Delta between biggest and smallest is one");
            	break;            	
            }

            // stop if we have one instance per machine
            if (firstGsc.getMachine().getProcessingUnitInstances().length ==1) 
            {
                log.info("Can't rebalanceInstanceCount anymore! - we have one instance on the machine");
            	break;            	
            }

            // stop if we have one instance within each container
            if (firstGsc.getProcessingUnitInstances().length ==1) 
            {
                log.info("Can't rebalanceInstanceCount anymore! - we have one instance within each container");
            	break;            	
            }

            String relocatedInstance = relocateSingleInstance(firstGsc,lastGsc,relocatedInstances );
            
            if (!relocatedInstance.equals("-1"))
            {
            	relocatedInstances.add(relocatedInstance);
            }

            // stop if all instances been relocated
            if (relocatedInstances.size() >= pu.getTotalNumberOfInstances())
            {
                log.info("Can't rebalanceInstanceCount anymore! - we moved all instances");
            	break;
            }
            
            gscs = getGscsSortedByNumInstances();
            firstGsc = gscs.first();
            lastGsc = gscs.last();
            retryCount ++;
            if (retryCount == 100 )
            {
                log.info("Can't rebalanceInstanceCount anymore!");
            	break;
            }
            
//            log.info("After move cycle - First GSC got:" + firstGsc.getProcessingUnitInstances(puName).length+ " instances" + " Last GSC got:" + lastGsc.getProcessingUnitInstances(puName).length+ " instances" );
        }

//        int delta = firstGsc.getProcessingUnitInstances().length - lastGsc.getProcessingUnitInstances().length;
//        log.info("  Number of instances per GSC is balanced, delta = " + Integer.toString(delta));        
        
        log.info( Integer.toString(relocatedInstances.size()) + " instances relocated!");

		log.info("  ---- rebalanceInstanceCount DONE ----");
		log.info("");
    }

    
    void rebalancePrimaryToBackup() throws Exception {
    	log.info("  ---- rebalancePrimaryToBackup STARTED ----");

    	Hashtable<String, ArrayList<SpaceInstance>> spacesPerHostList = getSpacesOnHosts(null);
    	ProcessingUnitInstance puInstances[] = getInstances();

    	int movedToBackup = 0;

    	HashSet<Integer> primaryInstancesRestarted = new HashSet<Integer>();
    	Hashtable<String,Integer> gscWithRestartedSpaces = new Hashtable<String, Integer>(); 

    	String hostaddress = spacesPerHostList.keys().nextElement();

    	//Assumes all machines have same number of GSCs
    	int numofGSCPerMachine = pu.getAdmin().getMachines().getMachineByHostAddress(hostaddress).getGridServiceContainers().getContainers().length;
    	log.info("  Num GSC per Machine = " + numofGSCPerMachine);

    	int machineCount = spacesPerHostList.size();
    	log.info("  Total Number of Machines Running space= " + machineCount);    	
    	
    	int maxAmountOfSpacestoRestartPerGSC = (pu.getSpace().getNumberOfInstances()/ numofGSCPerMachine) / machineCount ;
    	
    	if (maxAmountOfSpacestoRestartPerGSC < 1) {
    		maxAmountOfSpacestoRestartPerGSC = 1;
    	}
    	log.info("  Max Amount Of Primary Spaces to Restart Per GSC = " + maxAmountOfSpacestoRestartPerGSC );	
    	int amountOfPrimarysPerMachine =  Math.round(pu.getSpace().getNumberOfInstances() / machineCount) ;

    	for (int i=0;i<puInstances.length;i++)
    	{
    		if (isPrimary(puInstances[i]))
    		{
    			// Make sure we are moving a space we did not move before
    			if (!(primaryInstancesRestarted.contains(puInstances[i].getInstanceId())))
    			{
    				String GSC_ID = puInstances[i].getGridServiceContainer().getUid();			    	
    				boolean doRestart = true;
    				if (gscWithRestartedSpaces.containsKey(GSC_ID))
    				{
    					// check if restarted spaces on this GSC
    					int restartedSpaces = gscWithRestartedSpaces.get(GSC_ID);

    					if (restartedSpaces == maxAmountOfSpacestoRestartPerGSC)
    						doRestart = false;	
    				}

    				// check how many primaries we have on the machine. If we got the desired amount - we should not restart the space
    				String machineAddress = puInstances[i].getMachine().getHostAddress();
    				int primaryCount = getAmountOfPrimary(machineAddress);
    				if (primaryCount <= amountOfPrimarysPerMachine)
    				{
    					doRestart = false;
    				}

    				if (doRestart)
    				{
    					log.info("Moving Primary Space ID:" + puInstances[i].getInstanceId()+ " On Machine "+puInstances[i].getMachine().getHostAddress() + " into Backup mode");

    					puInstances[i].restartAndWait(10, TimeUnit.SECONDS);
    					primaryInstancesRestarted.add(puInstances[i].getInstanceId());	
    					if (gscWithRestartedSpaces.containsKey(GSC_ID))
    					{
    						int restartedSpaces = gscWithRestartedSpaces.get(GSC_ID);
    						restartedSpaces ++;
    						gscWithRestartedSpaces.put(GSC_ID,restartedSpaces );
    					}
    					else
    					{
    						gscWithRestartedSpaces.put(GSC_ID, 1);
    					}
    					log.info("gscWithRestartedSpaces:" +gscWithRestartedSpaces);
    					movedToBackup ++;

    				}
    			}
    		}
    	}

    	if (movedToBackup > 0)
    	{
    		getSpacesOnHosts("After Rebalancing:  ");    		
    	}
		log.info("  ==>> Moved " + movedToBackup +  " spaces into Backup Mode.");
    	log.info("  ---- rebalancePrimaryToBackup DONE ----");
    	log.info("");
    }
    
    
    
    
    void balancePrimariesOnAllHosts () throws Exception
    {
    	log.info("---- balancePrimariesOnAllHosts STARTED ----");    	
    	getSpacesOnHosts("Before Rebalancing:  ");
    	
    	//Step 1. Rebalance Instance Count across hosts
    	rebalanceInstanceCount();

    	//Step 2.  Rebalance Primary to Backup ration
    	rebalancePrimaryToBackup();
		
		log.info("---- balancePrimariesOnAllHosts DONE ----");		
    }
}
