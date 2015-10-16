. ./set-environment.sh


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
