package main

import (
	"fmt"
	"net"
	"strings"
)

func udpServer() []string {

	a, _ := net.Interfaces()
	adrs, _ := a[1].Addrs()
	ipv4adrs := strings.Split(adrs[0].String(), "/")[0]

	s, _ := net.ResolveUDPAddr("udp4", ipv4adrs+":8000")
	c, _ := net.ListenUDP("udp4", s)

	defer c.Close()

	fmt.Println("Server started at : " + ipv4adrs + ":8000")

	buffer := make([]byte, 512)
	for {
		n, _, _ := c.ReadFromUDP(buffer)
		if n != 0 {
			return strings.Split(string(buffer[0:n-1]), ",")
		}
	}
}

func udpClient() {
	data := ""
	for ip, dt := range mp {
		data += ip + ":"
		for _, d := range dt {
			data += d + ","
		}
		data += "\n"
	}

	sendPacket(data)
}
