import numpy as np
import pandas as pd
import seaborn as sns
import os
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import torch

import pytorch_lightning as pl  
import matplotlib.pyplot as plt
import wandb
import torchmetrics
import time

ds = "./data.csv"
mdl = 'scp'
wandb = False

cfg = dict(
    num_classes = 1,
    wandb = 'sdn',
    epochs = 25,
    lr = 1e-4,
    mdl = mdl,
    drp = 0.5,
    stateD = 86,
    lyrs = [40,20,1],
    name="scp"
)

torch.manual_seed(10)
np.random.seed(10)
torch.set_default_tensor_type(torch.FloatTensor)
device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")


if wandb:
    import wandb
    os.environ["WANDB_SILENT"] = "true"
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

class model(pl.LightningModule):
    def __init__(self ):
        super().__init__()
    
        pr = cfg["stateD"]
        
        self.l1 = nn.Linear(pr , cfg["lyrs"][0])
        self.l2 = nn.Linear(cfg["lyrs"][0], cfg["lyrs"][1])
        self.l3 = nn.Linear(cfg["lyrs"][1], cfg["lyrs"][2])

        self.dropout = nn.Dropout(cfg['drp'])
        self.lr = cfg['lr']
        self.ls = nn.MSELoss()

        self.metrics = {
            "mae" :  torchmetrics.MeanAbsoluteError() ,
            "cos_sim" : torchmetrics.CosineSimilarity(),
        }
        self.vmetrics = {
            "mae" :  torchmetrics.MeanAbsoluteError() ,
            "cos_sim" : torchmetrics.CosineSimilarity(),
        }
        
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
        print(x.shape , target.shape)
        out = self.forward( x )
        loss = self.loss( out,target)
        
        for i in self.metrics :
            self.metrics[i](out , target) 
            
        return {"loss":loss,"out": out,"target" : target }
    
    def training_epoch_end(self ,x):
        v = 0
        for i in x: v += i['loss']
        dt = {"train/loss": v/len(x) } 
        for i in self.metrics:
            dt["train/" + i] = self.metrics[i].compute()
            
        if wandb : wandb.log( dt , step = self.current_epoch )
        
        
    def validation_step(self, dt, bid ):
        x, target = dt
        x = x.to(device)
        target = target.to(device)
        
        out = self.forward( x[0] )
        loss = self.loss( out   , target[0] )
        
        for i in self.vmetrics :
            self.vmetrics[i](out , target[0]) 
        
        return {"loss":loss,"out": out,"target" : target }
    
    def validation_epoch_end(self , x):
        v = 0
        for i in x: v += i['loss']
            
        dt = {"val/loss": v/len(x) } 
        for i in self.vmetrics:
            dt["val/" + i] = self.vmetrics[i].compute()
            
        if wandb : wandb.log(dt, step = self.current_epoch )
    
    def configure_optimizers(self):
        optimizer = torch.optim.AdamW( self.parameters() , lr=self.lr  )
        return optimizer
    
md = model()

while True:
    df = pd.read_csv(ds)
    train_loader = DataLoader( data(df) , batch_size=2)
    trainer = pl.Trainer(max_epochs = cfg['epochs'] )
    trainer.fit( md , train_loader  )
    torch.save(md , cfg['name'] + ".pt")
    inp = torch.zeros((2,cfg["stateD"]))
    # Export the model
    torch.onnx.export(md, inp,"scp_model.onnx",export_params=True,opset_version=10,          
                    do_constant_folding=True,  
                    input_names = ['input'],   
                    output_names = ['output'], 
                    dynamic_axes={'input' : {0 : 'batch_size'},   
                                    'output' : {0 : 'batch_size'}})    
    

    
    time.sleep(1000)
