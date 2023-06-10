import numpy as np
import pandas as pd
# import seaborn as sns
import os
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import torch

import pytorch_lightning as pl  
# import matplotlib.pyplot as plt
import wandb
import time

ds = "./data.csv"
mdl = 'scp'
wandb = True

cfg = dict(
    num_classes = 1,
    wandb = 'sdn',
    epochs = 25,
    lr = 1e-4,
    mdl = mdl,
    drp = 0.5,
    stateD = 86,
    lyrs = [40,20,1],
    name="scp",
    sleep=200
)

torch.manual_seed(10)
np.random.seed(10)
torch.set_default_tensor_type(torch.FloatTensor)
device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")


if wandb:
    import wandb
    wandb.login(key="d8974649937177296215ffe125b70df41ddb76a3")
    run = wandb.init(project="sdn_scp", name= cfg['name'] , config = cfg )


class data(Dataset):
    def __init__(self , df ):
        self.df = df
    
    def __len__( self ):
        return df.shape[0]
    
    def __getitem__(self, i):
        x = torch.from_numpy(self.df.iloc[i][:cfg["stateD"]].to_numpy())
        y = torch.from_numpy(self.df.iloc[i][cfg["stateD"]:cfg["stateD"]+1].to_numpy())

        return x.float() , y.float()
    
print("Dataset" , data(pd.read_csv(ds))[0])

ar = []

class model(pl.LightningModule):
    def __init__(self ):
        super().__init__()
    
        pr = cfg["stateD"]
        self.loss_t = 0
        self.ct_t = 0

        self.l1 = nn.Linear(pr , cfg["lyrs"][0])
        self.l2 = nn.Linear(cfg["lyrs"][0], cfg["lyrs"][1])
        self.l3 = nn.Linear(cfg["lyrs"][1], cfg["lyrs"][2])

        self.dropout = nn.Dropout(cfg['drp'])
        self.lr = cfg['lr']
        self.ls = nn.MSELoss()
        
    def loss( self, x , y ):        
        return self.ls(x, y)
    
    def forward(self, x ):
        out = self.l1(x)
        out = self.l2(out)
        out = self.l3(out)
        return out
    
    def predict_step(self, batch, batch_idx: int , dataloader_idx: int = None):
        return self(batch)
            
    def training_step(self, dt , bid ):
        x, target = dt
        x = x.to(device)
        target = target.to(device)
        out = self.forward( x )
        loss = self.loss( out,target)
        self.ct_t += 1
        self.loss_t += loss

        return {"loss":loss,"out": out,"target" : target }
    

    def on_train_epoch_end(self):
        # dt = {"train/loss" : self.loss_t/self.ct_t }
        ar.append(self.loss_t/self.ct_t)
        # if wandb : wandb.log(dt)
        
        self.loss_t = 0
        self.ct_t = 0        
    
    def configure_optimizers(self):
        optimizer = torch.optim.AdamW( self.parameters() , lr=self.lr  )
        return optimizer
    
md = model()

def epoch_ct(): 
    ct = 0
    while True:
        ct += 1
        if ct < 10:
            yield 20
        elif ct < 20:
            yield 30
        # elif ct < 60:
        #     yield 50
        else:
            yield cfg["epochs"]

ep = epoch_ct()

import socket

while True:
    df = pd.read_csv(ds)
    
    if len(df) > 500:
        df = df.iloc[-500:]
        df.to_csv("./data.csv", index=False)

    train_loader = DataLoader( data(df) , batch_size=2)
    trainer = pl.Trainer(max_epochs = next(ep) )
    trainer.fit( md , train_loader )

    # torch.save(md , cfg['name'] + ".pt")
    inp = torch.zeros((2,cfg["stateD"]))
    
    # Export the model
    torch.onnx.export(md, inp,"scp_model.onnx",export_params=True,opset_version=10,          
                    do_constant_folding=True,  
                    input_names = ['input'],   
                    output_names = ['output'], 
                    dynamic_axes={'input' : {0 : 'batch_size'},   
                                    'output' : {0 : 'batch_size'}})    
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.sendto(bytes("update", "utf-8"), ("localhost", 4445))
    
    if wandb : wandb.log({ "train/step_loss" : sum(ar) / len(ar)  })
    ar = []
    time.sleep(cfg["sleep"])
