package main

import (
	"strconv"

	"github.com/go-ping/ping"
)

func pingHost(hs string) {
	pinger, er := ping.NewPinger(hs)
	pinger.SetPrivileged(true)
	pinger.Count = 1
	if er != nil {
		panic(er)
	}

	pinger.OnFinish = func(stats *ping.Statistics) {
		if _, er := mp[hs]; !er {
			mp[hs] = make([]string, 2)
		}
		t := stats.AvgRtt.Microseconds()
		mp[hs][0] = strconv.Itoa(int(t))
	}

	// blocks until finish
	er = pinger.Run()
	if er != nil {
		panic(er)
	}
}
