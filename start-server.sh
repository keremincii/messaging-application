#!/bin/bash
echo "Sohbet sunucusu Docker ile başlatılıyor..."
docker compose up -d --build
echo "Sunucu başlatıldı! Arka planda çalışmaya devam edecek."
