package main

import (
	"bytes"
	"fmt"
	"log"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

func ipefClient(hs string) {
	cmd := exec.Command("iperf3", "-u", "-c", hs, "-k", "2")

	// cmd.Stdin = strings.NewReader("and old falcon")

	var out bytes.Buffer
	cmd.Stdout = &out

	err := cmd.Run()

	if err != nil {
		log.Fatal(err)
	}

	s := out.String()
	ar := strings.Split(s, "\n")
	j1, _ := strconv.ParseFloat(strings.Split(ar[6], " ")[16], 32)
	j2, _ := strconv.ParseFloat(strings.Split(ar[7], " ")[16], 32)

	j := (j1 + j2) / 2
	if _, er := mp[hs]; !er {
		mp[hs] = make([]string, 2)
	}
	time.Sleep(5 * time.Millisecond)
	mp[hs][1] = fmt.Sprint(j)

}
