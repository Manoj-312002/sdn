package main

import (
	"fmt"
	"sync"
	"time"
)

var mp map[string][]string

func main() {
	// go iperfServ()
	// get list of host whose details are required
	ar := udpServer()
	fmt.Println(ar)
	for {
		// store ip address : metric arrayS
		mp = make(map[string][]string)

		var wg sync.WaitGroup

		for _, hs := range ar {
			wg.Add(1)
			go func(hs string) {
				pingHost(hs)
				wg.Done()
			}(hs)
		}

		for _, hs := range ar {
			ipefClient(hs)
		}

		wg.Wait()
		udpClient()
		fmt.Println(mp)

		time.Sleep(5 * time.Second)
	}
}
