# Deploy di ReceiptHub su AWS

> **Segnaposto**: nei file di questa cartella e nelle policy in `deploy/iam/`,
> `<ACCOUNT_ID>` e `<EC2_INSTANCE_ID>` vanno sostituiti con i valori reali del
> proprio account prima di incollarli in console. Sono tenuti fuori dal
> repository perche' identificano l'infrastruttura, non perche' siano segreti.

Una sola istanza EC2 esegue quattro container via Docker Compose: **Caddy** (reverse proxy e
certificati), **frontend** (Node, SSR), **backend** (Spring) e **Postgres**. Le immagini le
costruisce GitHub Actions e le pubblica su ECR; l'istanza si aggiorna via SSM a ogni push su
`master`. Lo storage delle ricevute resta su S3.

Frontend e backend stanno sulla **stessa origine**, separati per percorso: `/api/*` va al backend
(che gira con `server.servlet.context-path=/api`), tutto il resto al frontend. Di conseguenza in
produzione il browser non fa richieste cross-origin e il CORS non entra in gioco.

## File di questo repository

| File | A cosa serve |
|---|---|
| `compose.prod.yaml` | Lo stack. Non costruisce immagini: le prende dai tag nel `.env` |
| `compose.local.yaml` | Override per il PC: monta `~/.aws` nel backend per usare S3 in locale |
| `Caddyfile` | Instradamento `/api` e certificati automatici |
| `.env.example` | Da copiare in `.env` (che non va in git) |
| `deploy/instance-user-data.sh` | Bootstrap dell'istanza al primo avvio |
| `deploy/update.sh` | Aggiorna un servizio: lo invoca SSM dai workflow |
| `deploy/publish-config.sh` | Carica compose, Caddyfile e update.sh su S3 |
| `deploy/iam/*.json` | Le tre policy da incollare in console |
| `.github/workflows/deploy.yml` | Build, push su ECR, aggiornamento via SSM |

## Provare lo stack in locale

Serve un `.env` (copiato da `.env.example`) con almeno `POSTGRES_PASSWORD` e `AWS_S3_BUCKET`:
senza, compose si ferma subito invece di partire con valori sbagliati.

```
docker build -t receipthub-backend:local .
docker compose -f compose.prod.yaml -f compose.local.yaml up -d
```

Il frontend va costruito dal suo repository passando le `VITE_*` come build-arg, con
`VITE_API_BASE_URL=http://localhost:8081/api`.

## Fase 2 — Prima messa online

1. **Budget alert, prima di ogni altra cosa.** Billing → Budgets → Create budget → Cost budget,
   soglia 5 $, notifica alla tua email. Da qui in poi ogni risorsa costa.

2. **Carica la configurazione su S3** (dal PC, serve la CLI configurata):
   ```
   AWS_S3_BUCKET=backendsdcc-dev-files-001-<ACCOUNT_ID>-eu-north-1-an sh deploy/publish-config.sh
   ```

3. **Ruolo IAM per l'istanza** — IAM → Roles → Create role → AWS service → EC2. Allega:
   - `AmazonSSMManagedInstanceCore` (gestita da AWS: serve per la shell e per i deploy)
   - `AmazonEC2ContainerRegistryReadOnly` (gestita da AWS: per scaricare le immagini)
   - una policy inline con il contenuto di `deploy/iam/ec2-instance-policy.json` (accesso al solo
     bucket delle ricevute)

   Nome suggerito: `ReceiptHubInstanceRole`.

4. **Permessi per la CLI del tuo PC** — l'utente `utente-bello` con cui è configurata la CLI può
   accedere solo a S3, quindi da terminale non riusciresti né a spegnere l'istanza né a
   guardarne lo stato. IAM → Users → `utente-bello` → Add permissions → Create inline policy →
   JSON, incollando `deploy/iam/cli-user-policy.json`. Concede accensione, spegnimento e comandi
   SSM **solo** sulle istanze con tag `Project=ReceiptHub`, più la lettura dei costi.

5. **Security group** — in ingresso solo 80 e 443 da `0.0.0.0/0`. **La 22 resta chiusa**: si entra
   con Session Manager, quindi SSH non serve.

6. **Istanza EC2** — Launch instance:
   - AMI Amazon Linux 2023, tipo `t3.micro`
   - subnet pubblica, **Auto-assign public IP: Enable**
   - nessuna coppia di chiavi (`Proceed without a key pair`)
   - IAM instance profile: `ReceiptHubInstanceRole`
   - storage: 20 GB gp3
   - **Tag `Project` = `ReceiptHub`**: non è decorativo, la policy del punto 4 lo usa come
     condizione per permettere accensione e spegnimento
   - Advanced details → User data: il contenuto di `deploy/instance-user-data.sh`,
     **sostituendo** `<ACCOUNT_ID>` nella riga `BUCKET=` con l'ID del proprio account

7. Annota l'**Instance ID** (`i-...`): serve nei due passi successivi.

## Fase 3 — Aggiornamento automatico

1. **Due repository ECR** — ECR → Create repository, privati, nomi `receipthub-backend` e
   `receipthub-frontend`.

2. **Provider OIDC** — IAM → Identity providers → Add provider → OpenID Connect:
   - URL: `https://token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`

3. **Ruolo per GitHub** — IAM → Roles → Create role → Custom trust policy, incollando
   `deploy/iam/github-oidc-trust-policy.json`. Poi allega una policy inline con
   `deploy/iam/github-deploy-policy.json`, **sostituendo** `SOSTITUISCI-CON-INSTANCE-ID`.
   Nome suggerito: `ReceiptHubGitHubDeployRole`. Copiane l'ARN.

4. **Configura i due repository GitHub** (Settings → Secrets and variables → Actions):

   | Nome | Tipo | Valore | Dove |
   |---|---|---|---|
   | `AWS_DEPLOY_ROLE_ARN` | secret | ARN del ruolo del punto 3 | entrambi |
   | `AWS_REGION` | variable | `eu-north-1` | entrambi |
   | `EC2_INSTANCE_ID` | variable | `i-...` | entrambi |
   | `ECR_BACKEND_REPOSITORY` | variable | `receipthub-backend` | backend |
   | `ECR_FRONTEND_REPOSITORY` | variable | `receipthub-frontend` | frontend |
   | `VITE_API_BASE_URL` | variable | `http://IP-PUBBLICO/api` | frontend |
   | `VITE_FIREBASE_*` | variable | i quattro valori del `.env` locale | frontend |

   Le `VITE_*` stanno fra le *variables* e non fra i *secrets* di proposito: finiscono nel
   JavaScript servito al browser, quindi non sono segrete. La chiave web di Firebase è pubblica
   per progetto: a proteggere l'account sono gli authorized domains e le regole.

5. **Push su `master`** in uno dei due repository: il workflow costruisce, pubblica e aggiorna.

## Uso quotidiano

Spegnere l'istanza quando non ci lavori è la voce che tiene la spesa sui pochi euro al mese:

```
aws ec2 stop-instances --instance-ids i-xxxxxxxx
```
```
aws ec2 start-instances --instance-ids i-xxxxxxxx
```

L'IP pubblico **cambia a ogni riavvio** (scelta voluta: un Elastic IP si paga anche da spento).
Finché non c'è il dominio della Fase 4, dopo ogni accensione va aggiornata la variabile
`VITE_API_BASE_URL` su GitHub e rifatto un push.

Shell sull'istanza: EC2 → seleziona istanza → Connect → **Session Manager**.

Log dei container: `sudo docker compose -f /opt/receipthub/compose.prod.yaml logs -f`.

Su un'istanza creata prima che `AWS_S3_BUCKET` diventasse obbligatoria, aggiungila una volta a
`/opt/receipthub/.env` (il bootstrap ora la scrive da solo) e rifai `docker compose up -d backend`.

Se cambi `compose.prod.yaml` o il `Caddyfile`: rilancia `publish-config.sh` e ricopiali
sull'istanza (`aws s3 cp` dalla shell SSM), poi `docker compose up -d`.

## Fase 4 — Dominio e HTTPS

1. Registra un nome su DuckDNS e puntalo all'IP pubblico.
2. Sull'istanza, in `/opt/receipthub/.env`: `SITE_ADDRESS=nome.duckdns.org` e
   `PUBLIC_ORIGIN=https://nome.duckdns.org`, poi `docker compose up -d caddy`. Caddy richiede il
   certificato da solo.
3. Aggiungi il dominio agli **authorized domains** di Firebase, altrimenti il login non parte.
4. Aggiorna `VITE_API_BASE_URL` a `https://nome.duckdns.org/api` e fai un push.

Con il dominio, l'IP che cambia a ogni riavvio smette di essere un problema: basta uno script al
boot che aggiorni il record DuckDNS.
