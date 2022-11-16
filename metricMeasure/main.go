package main

import (
	"sync"
)

var ipMetric map[string][]int
var ipCount map[string]int
var chmp map[string]chan bool // for each host a timeout is kept

func main() {
	ipMetric = make(map[string][]int)
	ipCount = make(map[string]int)
	chmp = make(map[string]chan bool)

	logFile()

	go metricServer()
	for {
		// wait for info from co ntroller
		ar := controllerServer()
		// InfoL.Println("Nearby host : ", ar)

		var wg sync.WaitGroup

		for _, hs := range ar {
			wg.Add(1)
			go func(hs string) {
				mPacket(hs)
				wg.Done()
			}(hs)
		}

		wg.Wait()
	}
}
