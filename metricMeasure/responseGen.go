package main

import (
	"math"
	"strconv"
	"time"
)

/*
 * implements a parser for extracting metrics
 * checks if all 10 packets of [1 host] is received
	* if all packets are received send response is called
	* which calls packet module
	* else timeout message is sent after 1 sec
*/

func finishIteration(hs string) {
	chmp[hs] <- true
	sendResponse(hs)
	ipCount[hs] = -1
}

// this time out function waits for the above function to be called
// or calls send response after 2 second
func timeOut(hs string) {
	select {
	//* this part is set after 10 iterations (finishiteration)
	case <-chmp[hs]:
		InfoL.Println("Message sent")
	case <-time.After(2 * time.Second):
		sendResponse(hs)
		ipCount[hs] = -1
		InfoL.Println("Timeout Message sent")
	}
}

// * read the packet sent by nearby host and get check if it has got 10 iterations
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
		// * timeout is called for 1st packet
		go timeOut(hs)
	}

	//* difference between current time and time in packet is taken
	ipMetric[hs][ipCount[hs]] = int(time.Since(t).Microseconds())
	ipCount[hs] += 1

	if ipCount[hs] == 10 {
		finishIteration(hs)
	}
}

/*
 * calculates the average delay for each host
 * jitter = avg( delay - avg(delay) )
 */
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

	// forming udp packet
	sendPacket(hs + ":" + strconv.FormatFloat(delay, 'E', -1, 32) + "," + strconv.FormatFloat(jitter, 'E', -1, 32))
}
