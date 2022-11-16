package main

import (
	"log"
	"net"
	"time"
)

func mPacket(hs string) {
	ipv4adrs := getIp()

	for i := 0; i < 10; i++ {
		c, err := net.Dial("udp", hs+":8001")

		if err != nil {
			log.Fatal(err)
		}

		c.Write([]byte(ipv4adrs + "::" + time.Now().Format(time.RFC3339Nano)))
		c.Close()
	}
	// InfoL.Println("Packets sent to " + hs)
}
