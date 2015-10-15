. ./set-environment.sh

export HEAP_SIZE=$1
export GSC_NUM=$2
export ZONE=$3
export LUS_COUNT=$4
export GSM_COUNT=$5

# Compare if the NIC_ADDR of this machine matches one of the addresses of the LU
IFS=","
export MGMT_NODE=`for v in $LOOKUPLOCATORS; do if [ $NIC_ADDR = $v ];  then echo "1"; fi; done`

if [ -z "${GSM_COUNT}" ]; then
	echo "usage: gs-agent <gsc heap size> <gsc count> <zone> <lus count> <gsm count>"
	exit
fi


export GSC_JAVA_OPTIONS="${COMMON_JAVA_OPTIONS} -Xmx$HEAP_SIZE -Xms$HEAP_SIZE -Dcom.gs.zones=$ZONE"

nohup $JSHOMEDIR/bin/gs-agent.sh gsa.gsc $GSC_NUM gsa.global.gsm 0 gsa.global.lus 0 gsa.gsm $GSM_COUNT gsa.lus $LUS_COUNT &> $APP_DIR/logs/$ZONE-gs-agent-console-log &

