#!/bin/bash

export APP_DIR=/gigaspaces
sudo rm -rf $APP_DIR

export JDK_DOWNLOAD_URL=https://s3-us-west-2.amazonaws.com/gigaspaces-optum/jdk-7u21-linux-x64.tar.gz
export XAP_DOWNLOAD_URL=https://s3-us-west-2.amazonaws.com/gigaspaces-optum/gigaspaces-xap-premium-10.0.1-ga.zip
export SPACE_CONFIG_URL=https://s3-us-west-2.amazonaws.com/gigaspaces-optum/space-config.properties
export XAP_SCRIPTS_URL=https://s3-us-west-2.amazonaws.com/gigaspaces-optum/xap-scripts.zip


export JSHOMEDIR=$APP_DIR/gigaspaces-xap-premium-10.0.1-ga
export GS_HOME=$JSHOMEDIR
export JAVA_HOME=$APP_DIR/jdk/jdk1.7.0_21
export NIC_ADDR=`wget -qO- http://instance-data/latest/meta-data/local-ipv4`

export LOG_DIR=$XAP_TOOLS/logs


# Add JVM profiler
export COMMON_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Dcom.gs.multicast.enabled=false "  # Multicast

export GSM_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Xmx1g -Xms1g"
export LUS_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Xmx1g -Xms1g"
export GSA_JAVA_OPTIONS="$COMMON_JAVA_OPTIONS -Xmx256m -Xms256m"
export EXT_JAVA_OPTIONS="-Dcom.gs.transport_protocol.lrmi.max-threads=512 -Dcom.gs.transport_protocol.lrmi.max-conn-pool=2048"

# Log environment variables used
echo "COMMON_JAVA_OPTIONS: $COMMON_JAVA_OPTIONS" >> xap-env
echo "LOOKUPLOCATORS: $LOOKUPLOCATORS" >> xap-env
echo "LOOKUPGROUPS: $LOOKUPGROUPS" >> xap-env


if [ "$1" == "install" ]; then 
	if [ -d "$APP_DIR" ]; then
	        echo "$APP_DIR already exists"
	else
	        mkdir $APP_DIR
	fi

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

	mkdir $APP_DIR/scripts
	mkdir $APP_DIR/deploy
	mkdir $APP_DIR/work
	mkdir $APP_DIR/logs

	echo "Downloading XAP scripts..."
	curl -L -o xap-scripts.zip $XAP_SCRIPTS_URL
	#unzip xap-scripts.zip -d $APP_DIR/scripts
	unzip xap-scripts.zip
	
	echo "Copying this script to $APP_DIR/scripts"
	cp `pwd`/get-xap.sh /gigaspaces/scripts/set-environment.sh
	

	echo "Cleaning up"
	rm -rf gigaspaces-xap.zip xap-scripts.zip jdk.tar.gz

fi;


