import json
topology = json.load( open('./topo1.json', 'r') )

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.node import RemoteController,OVSKernelSwitch

class MyTopo( Topo ):
    def build( self ):

        nodes = []
        switches = []
        for i in range(len( topology )):
            nodes.append( self.addHost('h'+str(i+1) ) )
            if i+1 == 4:
                switches.append( self.addSwitch( 's'+str(i+1) , bw = 1 ) )
            else:
                switches.append( self.addSwitch( 's'+str(i+1)  ) )

        for i in range(len( topology )):
            self.addLink( nodes[ int(i) ] , switches[ int(i) ] )

        for i in topology:
            for j in topology[i]:
                self.addLink( switches[ int(i)-1 ] , switches[ int(j)-1 ]  )

if __name__ == '__main__' :
    topo = MyTopo()

topos = { 'nyc': ( lambda: MyTopo() ) }