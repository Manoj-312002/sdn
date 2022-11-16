package main

import (
	"math"
	"strconv"
	"time"
)

func finishIteration(hs string) {
	chmp[hs] <- true
	sendResponse(hs)
	ipCount[hs] = -1
}

// this time out function waits for the above function to be called
// or calls send response after 1 second
func timeOut(hs string) {
	select {
	case <-chmp[hs]:
		InfoL.Println("Message sent")
	case <-time.After(2 * time.Second):
		sendResponse(hs)
		ipCount[hs] = -1
		InfoL.Println("Timeout Message sent")
	}
}

func extractMetric(hs string, v string) {
	defer func() {
		if r := recover(); r != nil {
			ErrorL.Println(r)
		}
	}()

	t, err := time.Parse(time.RFC3339Nano, v)

	if err != nil {
		ErrorL.Println(err)
	}

	// check if there is array for the metric
	if _, er := ipMetric[hs]; !er {
		ipMetric[hs] = make([]int, 10)
		ipCount[hs] = -1
		chmp[hs] = make(chan bool, 1)
	}

	// if the packet is first of the iteration
	if ipCount[hs] == -1 {
		ipCount[hs] += 1
		go timeOut(hs)
	}

	ipMetric[hs][ipCount[hs]] = int(time.Since(t).Microseconds())
	ipCount[hs] += 1

	if ipCount[hs] == 10 {
		finishIteration(hs)
	}
}

func sendResponse(hs string) {
	delay := float64(0)

	for _, i := range ipMetric[hs] {
		delay += float64(i)
	}

	delay /= float64(ipCount[hs])
	jitter := float64(0)

	for _, i := range ipMetric[hs] {
		jitter += math.Abs(float64(i) - delay)
	}
	jitter /= float64(ipCount[hs])

	sendPacket(hs + ":" + strconv.FormatFloat(delay, 'E', -1, 32) + "," + strconv.FormatFloat(jitter, 'E', -1, 32))
}
