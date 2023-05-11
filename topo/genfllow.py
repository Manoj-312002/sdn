import random
n_files = 1
n_nodes = 14
random.seed(10)

for i in range(n_files):
    with open(f"./flows/flows{i}.txt" , 'w') as f:
        n_flows = 7
        rsd = random.sample(range(0,14), n_flows*2)
        ct = 0
        for i in range(n_flows):
            st = random.randint(1,20)
            priority = random.randint(1,100)
            duration = random.randint(10,20)
            bw = random.randint(15,30)
            src = rsd[ct]
            dst = rsd[ct+1]
            ct += 2
            f.write(f'{st},{priority},{duration},{bw},{src},{dst}' + "\n")
