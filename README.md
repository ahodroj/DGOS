# Data Grid Operating System
DGOS (Data Grid Operating System) is an in-memory data grid orchestration and scheduling stack consisting of GigaSpaces Cloudify, XAP, and Apache Mesos to build an elastic data grid on any cloud or data center. 

## Architecture Layers
* **Infrastructure Orchestrator (Cloudify)**: Responsible for provisioning a Mesos & Marathon cluster on any data center or cloud. As well as scaling out horizontal Mesos slaves nodes
* **Resource Scheduler (Mesos)**: Responsible for allocating (mostly) memory resources for provisioning XAP spaces as well, auto-rebalancing partitions, and elastic scaling. 
* **In-Memory Fabric (XAP)**: Responsibly for hosting large-scale in-memory data grids. 


