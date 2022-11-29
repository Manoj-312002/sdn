package main

import (
	"fmt"
)

type PHttpServer struct {
}

func main() {
	// proxy := goproxy.NewProxyHttpServer()
	// proxy.Verbose = false

	// proxy.OnRequest().HandleConnect(goproxy.AlwaysMitm)
	// proxy.OnRequest().DoFunc(func(req *http.Request, ctx *goproxy.ProxyCtx) (*http.Request, *http.Response) {
	// 	fmt.Println(req)
	// 	return req, nil
	// })

	// log.Fatal(http.ListenAndServe("127.0.0.1:8080", proxy))

	// a, _ := net.Interfaces()
	// for _, i := range a {
	// 	fmt.Println(i.Name)
	// }

	// sendPacket([]byte("sdf"), 19)
	for i := 0; i < 10; i++ {
		fmt.Println(i)
	}
}
