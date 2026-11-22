# Configurazione GitHub CI/CD & Aggiornamento In-App (VixFlix)

Questo progetto include tutto il necessario per:
1. **Compilazione automatica versionata ad ogni check-in (push)** tramite GitHub Actions (`.github/workflows/android-ci-release.yml`).
2. **Pubblicazione automatica su GitHub Releases** con tag semantico `v1.0.<run_number>` e `versionCode` incrementale.
3. **Controllo e Aggiornamento In-App** integrato nell'applicazione Android tramite le API ufficiali di GitHub Releases (`https://api.github.com/repos/{owner}/{repo}/releases/latest`).

## Come caricare su GitHub e attivare la build automatica

1. Crea un repository su GitHub (es. `tuo-utente/vixflix-android`) o usa il pulsante **Push to GitHub** nelle impostazioni di AI Studio.
2. Nel repository GitHub, vai in **Settings -> Secrets and variables -> Actions** e aggiungi i seguenti secrets:
   - Nome: `TMDB_API_KEY` — Valore: la tua chiave API TMDB
   - Nome: `SIMKL_CLIENT_ID` — Valore: il tuo Client ID pubblico Simkl API v2 (`OAuth2 Device Flow`)
3. Ad ogni `git push` sul branch `main` o `master`:
   - La GitHub Action incrementa automaticamente `CI_VERSION_NAME` (`1.0.X`) e `CI_VERSION_CODE` (`100 + X`).
   - Imposta `DEFAULT_GITHUB_REPO` automaticamente al nome del tuo repository (`owner/repo`).
   - Compila l'APK firmato `VixFlix-v1.0.X.apk` e crea una nuova Release su GitHub.
4. Nell'app **VixFlix**, apri la scheda **GitHub & Info** (o premi l'icona di aggiornamento in alto a destra), inserisci `owner/repo` se non già precompilato dalla CI, e verifica/scarica la nuova versione direttamente in app!
