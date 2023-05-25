import torch.nn as nn
import torch

import pytorch_lightning as pl  

cfg = dict(
    num_classes = 1,
    wandb = 'sdn',
    epochs = 25,
    lr = 1e-4,
    drp = 0.5,
    stateD = 86,
    lyrs = [40,20,1],
    name="scp",
    sleep=100
)


torch.manual_seed(10)
torch.set_default_tensor_type(torch.FloatTensor)
device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")


class model(pl.LightningModule):
    def __init__(self , nStates):
        super().__init__()
    
        pr = nStates
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
        dt = {"train/loss" : self.loss_t/self.ct_t }
        
        self.loss_t = 0
        self.ct_t = 0        
    
    def configure_optimizers(self):
        optimizer = torch.optim.AdamW( self.parameters() , lr=self.lr  )
        return optimizer

for i in range(20,400):
    md = model(i)
    inp = torch.zeros((2,i))
    torch.onnx.export(md, inp,f"./models/scp_model_{i}.onnx",export_params=True,opset_version=10,          
                        do_constant_folding=True,  
                        input_names = ['input'],   
                        output_names = ['output'], 
                        dynamic_axes={'input' : {0 : 'batch_size'},   
                                        'output' : {0 : 'batch_size'}})  
