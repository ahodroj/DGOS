#!/bin/bash

export APP_DIR=/gigaspaces

export JDK_DOWNLOAD_URL=https://s3-us-west-2.amazonaws.com/gigaspaces-optum/jdk-7u21-linux-x64.tar.gz
export XAP_DOWNLOAD_URL=https://s3-us-west-2.amazonaws.com/gigaspaces-optum/gigaspaces-xap-premium-10.0.1-ga.zip

export JSHOMEDIR=$APP_DIR/gigaspaces-xap-premium-10.0.1-ga
export GS_HOME=$JSHOMEDIR
export JAVA_HOME=$APP_DIR/jdk/jdk1.7.0_21
export NIC_ADDR=`wget -qO- http://instance-data/latest/meta-data/local-ipv4`	# Note, this only works on Amazon EC2

export LOG_DIR=$XAP_TOOLS/logs


# Add JVM profiler
export COMMON_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Dcom.gs.multicast.enabled=false "  # Disable Multicast

export GSM_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Xmx1g -Xms1g"
export LUS_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Xmx1g -Xms1g"
export GSA_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Xmx256m -Xms256m"

# Log environment variables used
echo "COMMON_JAVA_OPTIONS: $COMMON_JAVA_OPTIONS" >> xap-env.log
echo "LOOKUPLOCATORS: $LOOKUPLOCATORS" >> xap-env.log
echo "LOOKUPGROUPS: $LOOKUPGROUPS" >> xap-env.log
echo "GSC_HEAP_SIZE: $GSC_HEAP_SIZE" >> xap-env.log
echo "GSC_ZONE: $GSC_ZONE" >> xap-env.log
echo "GSC_COUNT: $GSC_COUNT" >> xap-env.log


if [ "$1" == "install" ]; then 
	echo "Starting clean"
	sudo rm -rf $APP_DIR

	echo "Downloading XAP 10"
	curl -L -o gigaspaces-xap.zip $XAP_DOWNLOAD_URL

	echo "Installing unzip"
	sudo apt-get install unzip

	echo "Extracting XAP10"
	unzip gigaspaces-xap.zip -d $APP_DIR


	echo "Download JDK 7"
	curl -L -o jdk.tar.gz $JDK_DOWNLOAD_URL

	mkdir $APP_DIR/jdk
	tar -zxvf jdk.tar.gz -C $APP_DIR/jdk
	
	mkdir $APP_DIR/deploy
	mkdir $APP_DIR/work
	mkdir $APP_DIR/logs


	echo "Cleaning up"
	rm -rf gigaspaces-xap.zip jdk.tar.gz

	echo "Successfully deployed XAP"
fi;


if [ "$1" == "start" ]; then
	# Compare if the NIC_ADDR of this machine matches one of the addresses of the LU
	IFS=","
	export MGMT_NODE=`for v in $LOOKUPLOCATORS; do if [ $NIC_ADDR = $v ];  then echo "1"; fi; done`

	export GSM_COUNT=$MGMT_NODE

	if [ -z "${GSM_COUNT}" ]; then
		export GSM_COUNT="0"
	fi

	export GSC_JAVA_OPTIONS="${COMMON_JAVA_OPTIONS} -Xmx$GSC_HEAP_SIZE -Xms$GSC_HEAP_SIZE -Dcom.gs.zones=$GSC_ZONE"

	# Start gs-agent
	nohup $JSHOMEDIR/bin/gs-agent.sh gsa.gsc $GSC_COUNT gsa.global.gsm 0 gsa.global.lus 0 gsa.gsm $GSM_COUNT gsa.lus $GSM_COUNT &> $APP_DIR/logs/$ZONE-gs-agent-console.log &

	# Start gs-webui if this is a management machine
	if [ $GSM_COUNT = "1" ]; then
		nohup ${JSHOMEDIR}/bin/gs-webui.sh $* &> $APP_DIR/gs-agent-webui.log & 
	fi
	
fi;




