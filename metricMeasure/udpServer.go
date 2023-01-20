package main

import (
	"net"
	"strings"
)

// * listens to packet from controller and returns ip addrs continuously
func controllerServer() []string {
	ipv4adrs := getIp()

	s, _ := net.ResolveUDPAddr("udp4", ipv4adrs+":8000")
	c, _ := net.ListenUDP("udp4", s)

	defer c.Close()

	// InfoL.Println("Server started at : " + ipv4adrs + ":8000")

	buffer := make([]byte, 512)
	for {
		n, _, _ := c.ReadFromUDP(buffer)
		if n != 0 {
			return strings.Split(string(buffer[0:n-1]), ",")
		}
	}
}

// * listens for packet nearby host
func metricServer() {
	ipv4adrs := getIp()

	s, _ := net.ResolveUDPAddr("udp4", ipv4adrs+":8001")
	c, _ := net.ListenUDP("udp4", s)

	defer func() {
		if r := recover(); r != nil {
			ErrorL.Println(r)
		}
	}()

	buffer := make([]byte, 512)
	for {
		n, _, _ := c.ReadFromUDP(buffer)
		if n != 0 {
			ar := strings.Split(string(buffer[0:n]), "::")
			extractMetric(ar[0], ar[1])
		}
	}
}
