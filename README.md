# EISENBERG TRADER
App educacional de análise técnica e simulação (Kotlin + Jetpack Compose + Room) e versão PWA em `pwa/index.html`.
Sem dinheiro real, sem corretoras, sem permissão de internet. Dados: SIM (fictícios) ou REAL (importados pelo usuário).

Estrutura: `domain/` (indicadores, sinais, tendência, risco, backtest), `data/` (Room), `provider/` (interface MarketDataProvider para MT5/APIs futuras), `ui/` (Compose).
Build: o workflow `.github/workflows/build-apk.yml` roda testes e gera `EISENBERG_TRADER.apk` (APK debug) como artefato.
Local: Android Studio (JDK 17) e `gradle assembleDebug`.
