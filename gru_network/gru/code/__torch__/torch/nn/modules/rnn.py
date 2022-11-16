class GRU(Module):
  __parameters__ = ["weight_ih_l0", "weight_hh_l0", "bias_ih_l0", "bias_hh_l0", ]
  __buffers__ = []
  weight_ih_l0 : Tensor
  weight_hh_l0 : Tensor
  bias_ih_l0 : Tensor
  bias_hh_l0 : Tensor
  training : bool
  _is_full_backward_hook : Optional[bool]
  def forward(self: __torch__.torch.nn.modules.rnn.GRU,
    x: Tensor) -> Tensor:
    bias_hh_l0 = self.bias_hh_l0
    bias_ih_l0 = self.bias_ih_l0
    weight_hh_l0 = self.weight_hh_l0
    weight_ih_l0 = self.weight_ih_l0
    max_batch_size = ops.prim.NumToTensor(torch.size(x, 0))
    hx = torch.zeros([1, int(max_batch_size), 1024], dtype=6, layout=None, device=torch.device("cpu"), pin_memory=False)
    _0 = [weight_ih_l0, weight_hh_l0, bias_ih_l0, bias_hh_l0]
    out, _1 = torch.gru(x, hx, _0, True, 1, 0., True, False, True)
    return out
