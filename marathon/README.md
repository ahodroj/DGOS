## Running GigaSpaces XAP on Apache Marathon/Mesos
1. Change the number of ```instances``` and ```LOOKUPLOCATORS``` addresses
2. Send POST request to Marathon endpoint with XAP grid JSON definition:
```
curl -X POST -H "Content-type: application/json" --data-binary @xap-grid.json http://<marathon-ip>:8080/v2/apps/
```
