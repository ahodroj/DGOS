# Data Grid Operating System
DGOS (Data Grid Operating System) is an in-memory data grid orchestration and scheduling stack consisting of GigaSpaces Cloudify, XAP, and Apache Mesos to build an elastic data grid on any cloud or data center. 

## Architecture Layers
* **Infrastructure Orchestrator (Cloudify)**: Responsible for provisioning a Mesos & Marathon cluster on any data center or cloud. As well as scaling out horizontal Mesos slaves nodes
* **Resource Scheduler (Mesos)**: Responsible for allocating (mostly) memory resources for provisioning XAP spaces as well, auto-rebalancing partitions, and elastic scaling. 
* **In-Memory Fabric (XAP)**: Responsible for hosting large-scale in-memory data grids. 




##### Deployment of XAP on Marathon/Mesos

The project includes a utility tool that one can use to fill the space with data from *.csv files or generate random entities. To run the tool, execute next script:

```
curl -X POST -H "Content-type: application/json" --data-binary @xap-grid.json http://10.8.1.254:8080/v2/apps/
```