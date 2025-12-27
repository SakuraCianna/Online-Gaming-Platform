# Python Game AI Service

## 部署步骤

```bash
# 1. 进入目录
cd python-game

# 2. 创建虚拟环境
python -m venv venv

# 3. 激活虚拟环境 (Windows)
venv\Scripts\activate

# 4. 安装 PyTorch GPU 版本 (CUDA 12.4)
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124

# 5. 安装其他依赖
pip install -r requirements.txt

# 6. 启动服务
python run.py
```

服务地址: http://localhost:8000
API文档: http://localhost:8000/docs
