"""
    reads the json file and parses the topology
    uses ovs switch
    calls the metric gen go module in all host
"""

#!/usr/bin/python
from asyncio import protocols
import json
from operator import imod
topology = json.load( open('./topo2.json', 'r') )

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.node import RemoteController,OVSSwitch,OVSController,OVSKernelSwitch
from mininet.log import setLogLevel, info

class nycT( Topo ):
    def build( self ):
        nodes = []
        switches = []
        for i in range(len( topology )):
            nodes.append( self.addHost('h'+str(i+1) ,  ip="10.0.0." + str(i+1) + "/8" ) , )
            switches.append( self.addSwitch( 's'+str(i+1) ) )

        for i in range(len( topology )):
            self.addLink( nodes[ int(i) ] , switches[ int(i) ] )

        for i in topology:
            for j in topology[i]:
                self.addLink( switches[ int(i)-1 ] , switches[ int(j)-1 ] , bw=1 , delay=0.1 )

class ovs( OVSSwitch ):
    def __init__(self, name, failMode='secure', datapath='kernel', inband=False, protocols="OpenFlow13", reconnectms=1000, stp=False, batch=False, **params):
        super().__init__(name, failMode, datapath, inband, protocols, reconnectms, stp, batch, **params)

topo = nycT()
setLogLevel( 'info' )
c1 = RemoteController(name='c1', ip='localhost' , port=6653 , protocols="OpenFlow13"  )
net = Mininet(topo=topo , controller=c1 , switch=ovs )

hosts = []
for i in range(len(topology)):
    hosts.append( net.get('h'+str(i+1)) )
    # hosts[-1].cmd('echo 0 >/proc/sys/net/ipv4/icmp_echo_ignore_broadcasts')
    # hosts[-1].cmd('iperf3 -s &')
    hosts[-1].cmd('/home/manoj/sdn/onos-apps/metricMeasure/metricGen &')
    # hosts[-1].cmd(f'chmod +x /home/manoj/sdn/onos-apps/traffic/n{i+1}.sh &')
    # hosts[-1].cmd(f'./home/manoj/sdn/onos-apps/traffic/n{i+1}.sh &')
    # print(x)

net.start()
net.pingAll()

CLI(net)
net.stop()