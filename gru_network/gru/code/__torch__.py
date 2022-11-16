class RNNModel(Module):
  __parameters__ = []
  __buffers__ = []
  training : bool
  _is_full_backward_hook : Optional[bool]
  rnn : __torch__.torch.nn.modules.rnn.GRU
  dropout : __torch__.torch.nn.modules.dropout.Dropout
  fc : __torch__.torch.nn.modules.linear.Linear
  ls : __torch__.torch.nn.modules.loss.MSELoss
  def forward(self: __torch__.RNNModel,
    x: Tensor) -> Tensor:
    fc = self.fc
    rnn = self.rnn
    _0 = torch.slice((rnn).forward(x, ), 0, 0, 9223372036854775807)
    input = torch.slice(torch.select(_0, 1, -1), 1, 0, 9223372036854775807)
    return (fc).forward(input, )
