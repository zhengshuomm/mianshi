from __future__ import annotations

import argparse
from pathlib import Path

try:
    import torch
    import torch.nn as nn
except ModuleNotFoundError as exc:
    raise SystemExit(
        "PyTorch is not installed in this Python environment.\n"
        "Tip: PyTorch usually supports Python 3.9-3.12. "
        "Create a 3.11/3.12 virtualenv and install torch first."
    ) from exc


class SimpleMLP(nn.Module):
    """A tiny classifier for ONNX export demo."""

    def __init__(self, in_features: int = 16, hidden: int = 32, num_classes: int = 4) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(in_features, hidden),
            nn.ReLU(),
            nn.Linear(hidden, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


def export_to_onnx(
    model: nn.Module,
    output_path: Path,
    input_dim: int,
    opset: int = 17,
) -> None:
    model.eval()
    dummy_input = torch.randn(1, input_dim)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    torch.onnx.export(
        model,
        dummy_input,
        str(output_path),
        export_params=True,
        opset_version=opset,
        do_constant_folding=True,
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={
            "input": {0: "batch_size"},
            "logits": {0: "batch_size"},
        },
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create a PyTorch model and export it to ONNX.")
    parser.add_argument("--out", type=Path, default=Path("model.onnx"), help="ONNX output path")
    parser.add_argument("--input-dim", type=int, default=16, help="Input feature dimension")
    parser.add_argument("--hidden", type=int, default=32, help="Hidden layer size")
    parser.add_argument("--num-classes", type=int, default=4, help="Number of classes")
    parser.add_argument("--opset", type=int, default=17, help="ONNX opset version")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    model = SimpleMLP(
        in_features=args.input_dim,
        hidden=args.hidden,
        num_classes=args.num_classes,
    )
    export_to_onnx(
        model=model,
        output_path=args.out,
        input_dim=args.input_dim,
        opset=args.opset,
    )
    print(f"ONNX model exported to: {args.out.resolve()}")


if __name__ == "__main__":
    main()
