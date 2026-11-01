#!/bin/bash
set -e

echo "=========================================="
echo "  🚀 ScanTaker Oracle Cloud Auto-Installer"
echo "=========================================="

# 1. Update system packages
echo "[1/4] Updating Ubuntu packages..."
sudo apt-get update -y && sudo apt-get upgrade -y
sudo apt-get install -y curl git ufw

# 2. Install Docker & Docker Compose
echo "[2/4] Installing Docker & Docker Compose..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    sudo systemctl enable docker
    sudo systemctl start docker
    rm get-docker.sh
fi

# 3. Configure Ubuntu Firewall
echo "[3/4] Configuring firewall ports (80, 443, 8081)..."
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8081 -j ACCEPT || true
sudo netfilter-persistent save 2>/dev/null || true

# 4. Start Docker Compose Stack
echo "[4/4] Starting PostgreSQL & Spring Boot API..."
sudo docker compose down 2>/dev/null || true
sudo docker compose up -d --build

echo "=========================================="
echo "  ✅ ScanTaker Backend is LIVE on Oracle!"
echo "  Test URL: http://$(curl -s ifconfig.me):8081/api/scans"
echo "=========================================="
