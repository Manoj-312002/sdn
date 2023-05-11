package main

/*
 * simple logger extension saves content to hostlogs.txt
 * 3 types info , error , warn
 */
import (
	"log"
	"net"
	"os"
	"strings"
)

var (
	WarningL *log.Logger
	InfoL    *log.Logger
	ErrorL   *log.Logger
)

func getIp() string {
	a, err := net.Interfaces()
	if err != nil {
		ErrorL.Println(err)
	}

	adrs, err := a[1].Addrs()
	if err != nil {
		ErrorL.Println(err)
	}

	ipv4adrs := strings.Split(adrs[0].String(), "/")[0]
	return ipv4adrs
}

func logFile() {
	file, err := os.OpenFile("hostLogs.txt", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0666)
	if err != nil {
		log.Fatal(err)
	}

	ipv4adrs := getIp()

	InfoL = log.New(file, "INFO: "+"["+ipv4adrs+"] :", log.Ldate|log.Ltime)
	WarningL = log.New(file, "WARNING: "+"["+ipv4adrs+"] :", log.Ldate|log.Ltime)
	ErrorL = log.New(file, "ERROR: "+"["+ipv4adrs+"] :", log.Ldate|log.Ltime)
}
