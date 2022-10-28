package main

import (
	"bytes"
	"log"
	"os/exec"
	"strconv"
	"strings"
)

func main() {

	cmd := exec.Command("iperf3", "-c", "10.0.0.2", "-u", "-k", "2")

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

}
