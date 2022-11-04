package main

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
	sendPacket([]byte("sdf"))
	// a, _ := net.Interfaces()
	// for _, i := range a {
	// 	fmt.Println(i.Name)
	// }
	// adrs, _ := a[15].Addrs()
	// ipv4adrs := strings.Split(adrs[0].String(), "/")[0]
	// fmt.Println(a[15].Name)
	// fmt.Println(ipv4adrs)
}
