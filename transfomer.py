import torch
import torch.nn as nn
import torch.nn.functional as F
import math

class MultiHeadSelfAttention(nn.Module):
    def __init__(self, d_model, num_heads):
        super().__init__()
        assert d_model % num_heads == 0
        
        self.d_model = d_model
        self.num_heads = num_heads
        self.head_dim = d_model // num_heads
        
        self.qkv = nn.Linear(d_model, d_model * 3)
        self.out = nn.Linear(d_model, d_model)
        
    def forward(self, x, mask=None):
        B, T, C = x.shape  # batch, seq_len, d_model
        
        qkv = self.qkv(x)  # (B, T, 3C)
        qkv = qkv.reshape(B, T, 3, self.num_heads, self.head_dim)
        qkv = qkv.permute(2, 0, 3, 1, 4)  # (3, B, heads, T, head_dim)
        
        q, k, v = qkv[0], qkv[1], qkv[2]
        
        attn_scores = (q @ k.transpose(-2, -1)) / math.sqrt(self.head_dim)
        
        if mask is not None:
            attn_scores = attn_scores.masked_fill(mask == 0, -1e9)
        
        attn_probs = F.softmax(attn_scores, dim=-1)
        attn_output = attn_probs @ v  # (B, heads, T, head_dim)
        
        attn_output = attn_output.transpose(1, 2).reshape(B, T, C)
        
        return self.out(attn_output)


class FeedForward(nn.Module):
    def __init__(self, d_model, d_ff):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(d_model, d_ff),
            nn.GELU(),
            nn.Linear(d_ff, d_model)
        )
        
    def forward(self, x):
        return self.net(x)


class TransformerBlock(nn.Module):
    def __init__(self, d_model, num_heads, d_ff, dropout=0.1):
        super().__init__()
        
        self.ln1 = nn.LayerNorm(d_model)
        self.ln2 = nn.LayerNorm(d_model)
        
        self.attn = MultiHeadSelfAttention(d_model, num_heads)
        self.ffn = FeedForward(d_model, d_ff)
        
        self.dropout = nn.Dropout(dropout)
        
    def forward(self, x, mask=None):
        # Self-Attention
        x = x + self.dropout(self.attn(self.ln1(x), mask))
        
        # Feed Forward
        x = x + self.dropout(self.ffn(self.ln2(x)))
        
        return x