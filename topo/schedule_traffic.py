from mininet.net import Mininet
from mininet.node import RemoteController , UserSwitch , OVSSwitch
from mininet.link import TCLink
from mininet.cli import CLI
from mininet.log import setLogLevel, info
from mininet.topo import Topo
# from mininet.node import CPULimitedHost

import os
import random
import time
import sys
import re
import socket
import json
from threading import Thread
 
# np.random.seed(10)
sampling_interval = '1'  # seconds
topology = json.load( open('./json/nyc.json', 'r') )
repeat = 50
random.seed(10)

cnv = ['0']
for i in range(1,len(topology)+1):
    cnv.append( str(i).zfill(2) )

class nycT( Topo ):
    def __init__(self, **params ):
        Topo.__init__(self, **params)

        nodes = []
        switches = []
        for i in range(len( topology )):
            nodes.append( self.addHost('h'+str(i+1) ,  ip="10.0.0." + str(i+1) + "/8"  ) )
            switches.append( self.addSwitch( name = "s"+str(i+1) , dpid="0"*10 + cnv[i+1] ) )

        for i in range(len( topology )):
            self.addLink( nodes[ int(i) ] , switches[ int(i) ] )

        for i in topology:
            for j in topology[i]:
                bw = int(f1.readline().split(",")[-1])
                # de = random.randint(10,120)
                self.addLink( switches[ int(i)-1 ] , switches[ int(j)-1 ] , cls=TCLink , bw=bw )


def generate_flows(id, duration, net, log_dir, bw , src , dst):

    """
    Generate Elephant flows
    May use either tcp or udp
    """

    hosts = net.hosts

    # select random src and dst
    end_points = hosts[src] , hosts[dst]
    src = net.get(str(end_points[0]))
    dst = net.get(str(end_points[1]))

    # select connection params
    protocol = " --udp"
    port_argument = "65525"
    bandwidth_argument = bw

    # create cmd
    server_cmd = "iperf -s"
    server_cmd += protocol
    server_cmd += " -p "
    server_cmd += port_argument
    # server_cmd += " -i "
    # server_cmd += sampling_interval
    server_cmd += " -y C"
    server_cmd += " > "
    server_cmd += log_dir + "/flow_%003d" % id + ".txt 2>&1 "
    server_cmd += " & "

    client_cmd = "iperf -c "
    client_cmd += dst.IP() + " "
    client_cmd += protocol
    client_cmd += " -p "
    client_cmd += port_argument
    # if protocol == "--udp":

    client_cmd += " -b "
    client_cmd += bandwidth_argument
    client_cmd += " -t "
    client_cmd += str(duration)
    client_cmd += " & "

    # send the cmd
    dst.cmdPrint(server_cmd)
    src.cmdPrint(client_cmd)


def sendStart(id_ref ,priority , bw , src , dst, start , duration):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.sendto(bytes("start,{0},{1},{2},{3},{4}".format(id_ref,priority,bw,src,dst), "utf-8"), ("localhost", 4445))
    # print(f"Starting {id_ref} at {start} ,{src} to {dst} of {bw} MBPS {duration}s long" , time.strftime("%H:%M:%S", time.localtime()))

def stop(id_ref,duration):
    time.sleep(duration)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.sendto(bytes("stop,{0}".format(id_ref), "utf-8"), ("localhost", 4445))
    # print("Stopping" , id_ref , time.strftime("%H:%M:%S", time.localtime())  )

def sendDone():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.sendto(bytes("done", "utf-8"), ("localhost", 4445))

f1 = open("./json/nycbw.txt" , 'r')

topo = nycT()
setLogLevel( 'info' )
c1 = RemoteController(name='c0', ip='localhost' , port=6633 )

sw = UserSwitch
net = Mininet(topo=topo , controller=c1 , switch=sw )

print(net.switches)
for i in net.switches:
    print(i.dpid)

net.start()

for i in range(len(topology)):
    h = net.get('h'+str(i+1))
    h.cmd('ethtool -K h' + str(i+1) + "-eth0 tx off")

net.pingAll(0.01)
net.staticArp()
start = input("Press enter to start")

for seq in range(repeat):
    flows = os.listdir("./flows")
    # flows = random.sample(flows , len(flows))
    
    print(flows)
    for i in flows:
        print(i)
        with open("./flows/" + i,'r') as f:
            log_dir = "./flowsOut/" + str(seq) + "/" + i.split('.')[0] 

            isExist = os.path.exists(log_dir)
            if not isExist:
                os.makedirs(log_dir)

            context = f.read()
            fls = context.split("\n")[:-1]
            fln = []
            print(fls)
            for j in fls:
                fl = [int(x) for x in j.split(",")]
                fln.append(fl)
            # fln.sort(key=lambda x: x[0])
            
            pr = 0
            id_ref = 0
            thrs = []
            for j in fln:
                time.sleep(j[0] - pr)
                start , priority , bw , duration , src , dst = j
                
                # sendStart(id_ref, priority , bw , src , dst, duration)
                # generate_flows(id_ref, duration , net, log_dir , str(bw)+ "M" , src, dst )
                t = Thread(target=sendStart,args=(id_ref, priority , bw , src , dst, start, duration)); t.start(); thrs.append(t)
                t = Thread(target=generate_flows,args=(id_ref, duration , net, log_dir , str(bw)+ "M" , src, dst )); t.start(); thrs.append(t)
                t = Thread(target=stop,args=(id_ref,duration+1)); t.start(); thrs.append(t)

                pr = start
                id_ref += 1
            
            for x in thrs:
                x.join()
            time.sleep(2)
        sendDone()
    time.sleep(5)
CLI(net)

net.stop()