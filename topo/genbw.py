import random
import json

random.seed(10)
f = open("./json/nycbw.txt" , 'w')
topology = json.load( open('./json/nyc.json', 'r') )

for i in topology:
    for j in topology[i]:
        n = random.randint(7,30)
        f.write(f'{i},{j},{n}'+'\n')
