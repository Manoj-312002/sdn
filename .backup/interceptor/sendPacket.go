package main

import (
	"fmt"
	"net"
	"strings"
	"syscall"

	"github.com/ghedo/go.pkt/packet"
	"github.com/ghedo/go.pkt/packet/eth"
	"github.com/ghedo/go.pkt/packet/ipv4"
	"github.com/ghedo/go.pkt/packet/raw"
	"github.com/ghedo/go.pkt/packet/udp"
)

func sendPacket(data []byte, idx int) {
	a, _ := net.Interfaces()
	fmt.Println(a[idx].Name)
	adrs, _ := a[idx].Addrs()
	ipv4adrs := strings.Split(adrs[0].String(), "/")[0]
	dt_pkt := raw.Make()
	dt_pkt.Data = data

	udp_pkt := udp.Make()
	udp_pkt.DstPort = 8000
	udp_pkt.SrcPort = 8000
	udp_pkt.SetPayload(dt_pkt)

	ip_pkt := ipv4.Make()
	ip_pkt.SrcAddr = net.ParseIP(ipv4adrs)
	ip_pkt.DstAddr = net.ParseIP(ipv4adrs)
	ip_pkt.SetPayload(udp_pkt)

	eth_pkt := eth.Make()
	eth_pkt.SrcAddr, _ = net.ParseMAC("3a:17:cc:0d:29:20")
	eth_pkt.DstAddr, _ = net.ParseMAC("00:00:00:00:00:00")
	eth_pkt.SetPayload(ip_pkt)

	fmt.Println(eth_pkt)

	var eb, ib, ub, db packet.Buffer

	eb.Init(make([]byte, 64))
	eth_pkt.Pack(&eb)
	ib.Init(make([]byte, 64))
	ip_pkt.Pack(&ib)
	ub.Init(make([]byte, 64))
	udp_pkt.Pack(&ub)
	db.Init(make([]byte, 64))
	dt_pkt.Pack(&db)

	dt := append(eb.Buffer()[:eb.LayerLen()], ib.Buffer()[:ib.LayerLen()]...)
	dt = append(dt, ub.Buffer()[:ub.LayerLen()]...)
	dt = append(dt, db.Buffer()[:db.LayerLen()]...)

	fd, _ := syscall.Socket(syscall.AF_PACKET, syscall.SOCK_RAW, syscall.ETH_P_ALL)

	var addr syscall.SockaddrLinklayer

	addr.Protocol = syscall.AF_INET
	addr.Ifindex = a[idx].Index
	addr.Hatype = syscall.ARPHRD_ETHER

	syscall.Sendto(fd, dt, 0, &addr)
}
