import random
n_files = 20
n_nodes = 14
random.seed(10)

for i in range(n_files):
    with open(f"./flows/flows{i}.txt" , 'w') as f:
        n_flows = 3
        rsd = random.sample(range(0,14), n_flows*2)
        ct = 0
        ar = []
        for i in range(n_flows):
            st = random.randint(1,20)
            priority = random.randint(1,50)
            duration = random.randint(20,40)
            bw = random.randint(15,35)
            src = rsd[ct]
            dst = rsd[ct+1]
            ct += 2
            ar.append([st,priority,duration,bw,src,dst])
        
        ar.sort(key=lambda x : x[0])
        for i in ar:
            f.write(f'{i[0]},{i[1]},{i[2]},{i[3]},{i[4]},{i[5]}' + "\n")
