# ReceiptHub — Backend

API REST che regge **ReceiptHub**, la piattaforma su cui i clienti archiviano le ricevute dei
propri acquisti e ne ricavano statistiche. Una ricevuta può essere compilata a mano e scaricata
in PDF, oppure caricata già in PDF: in quel caso il backend ne estrae righe, importi e metodo di
pagamento e li salva sul database. Sopra ai dati c'è un chatbot che risponde in linguaggio
naturale interrogando gli stessi servizi dell'API, con gli stessi controlli di ruolo.

Progetto per il corso di **Sistemi Distribuiti e Cloud Computing**.

- **Online**: <https://receipthub.duckdns.org>
- **Frontend**: repository separato (React Router 7 + TypeScript)
- **Deploy**: [`DEPLOY.md`](DEPLOY.md) — AWS EC2, ECR, S3, GitHub Actions via OIDC

> L'istanza EC2 viene spenta quando non serve, per contenere i costi: se il dominio non risponde,
> è probabilmente spenta.

---

## Indice

- [Funzionalità](#funzionalità)
- [Architettura](#architettura)
- [Stack](#stack)
- [Struttura del progetto](#struttura-del-progetto)
- [Avvio in locale](#avvio-in-locale)
- [Configurazione](#configurazione)
- [API](#api)
- [Autenticazione e ruoli](#autenticazione-e-ruoli)
- [Sicurezza](#sicurezza)
- [Il chatbot (RiceVito)](#il-chatbot-ricevito)
- [Deploy](#deploy)
- [Stato e limiti noti](#stato-e-limiti-noti)

---

## Funzionalità

**Per il cliente**

- Registrazione e login con Firebase Authentication, profilo completato lato backend
- Inserimento di una ricevuta riga per riga, con validazione di importi, date e carta
- Download della ricevuta in PDF (generato con OpenPDF/iText) via URL S3 firmato e a scadenza
- Caricamento di un PDF già esistente: il parser ne ricava codice, data, righe e totale
- Elenco delle proprie ricevute, ordinabile per data o per importo
- Ricerca *tollerante agli errori di battitura* per codice ricevuta o nome prodotto
- Ricerca per intervallo di importo e per ultime quattro cifre della carta
- Esportazione in CSV e statistiche personali (prodotto del mese, prodotto di un periodo)

**Per l'amministratore**

- Catalogo prodotti: creazione e cancellazione
- Ricerca clienti per nome, cognome, email o codice fiscale, sempre fuzzy
- Modifica del profilo di un cliente
- Cruscotto statistiche su un periodo: fatturato nel tempo, prodotti più venduti, distribuzione
  dei metodi di pagamento, clienti che hanno speso di più, riepilogo aggregato

**Trasversale**

- Chatbot *RiceVito*: domande in italiano sulle proprie ricevute (o su tutte, se amministratore)

---

## Architettura

In produzione tutto gira su **una sola istanza EC2**, con quattro container orchestrati da Docker
Compose. Frontend e backend stanno sulla stessa origine e sono separati per percorso, quindi il
browser non fa mai richieste cross-origin.

```
                    ┌──────────────────────────────────────────┐
   browser ──443──► │ Caddy  (TLS automatico, reverse proxy)    │
                    │   /api/*  ──► backend:8080  (Spring Boot) │
                    │   /*      ──► frontend:3000 (React SSR)   │
                    └───────────┬──────────────────┬────────────┘
                                │                  │
                          ┌─────▼─────┐      ┌─────▼──────┐
                          │ Postgres  │      │  servizi   │
                          │ (volume)  │      │  esterni   │
                          └───────────┘      └─────┬──────┘
                                                   │
                        S3 (PDF) · Firebase Auth (token) · OpenAI (chat)
```

Punti che vale la pena notare:

- **API stateless.** Nessuna sessione lato server: ogni richiesta porta il proprio token Firebase.
  Anche la cronologia della chat arriva dal client a ogni domanda, quindi due istanze del backend
  dietro un bilanciatore resterebbero intercambiabili.
- **Nessuna credenziale AWS nel codice.** Su EC2 le prende il ruolo IAM dell'istanza, in locale il
  profilo `~/.aws` montato in sola lettura (vedi `compose.local.yaml`).
- **I PDF non passano dal database.** Stanno su S3; il backend salva solo la chiave e restituisce
  al client un URL firmato a scadenza.

## Stack

| | |
|---|---|
| Linguaggio | Java 21 |
| Framework | Spring Boot 3.5.3 (Web, Data JPA, Security, Validation) |
| Build | Gradle 8.14 (wrapper incluso) |
| Database | PostgreSQL 15, accesso via Hibernate |
| Autenticazione | Firebase Authentication, validata come OAuth2 Resource Server (JWT) |
| Storage | AWS S3 (SDK v2), URL presigned |
| PDF | OpenPDF 2.2.4 / iText 5 per la generazione, parser custom in lettura |
| Ricerca fuzzy | Apache Commons Text (distanza di Levenshtein) |
| Chatbot | Spring AI 1.1.8 + OpenAI `gpt-5-mini`, con tool calling |
| Container | Docker multi-stage, Caddy come reverse proxy |

## Struttura del progetto

```
src/main/java/org/backendsdcc/
├── controllers/     5 controller REST: Receipt, Product, User, Stats, Chat
├── models/          entità JPA: User, Receipt, Purchase, Product + enum PaymentMethod
├── repositories/    Spring Data JPA
├── services/        logica applicativa (PDF, S3, ricerca, statistiche, chat)
└── support/
    ├── ai/          strumenti esposti al modello + limitatore di richieste
    ├── comparators/ ordinamento ricevute per data o importo
    ├── config/      SecurityConfig (JWT Firebase, CORS, ruoli), S3Config
    ├── dto/         oggetti di trasporto: nessuna entità JPA esce dai controller
    ├── exceptions/  eccezioni applicative + GlobalExceptionHandler
    ├── pdf/         generazione e parsing dei PDF
    └── validators/  carta di credito e date

deploy/              script di bootstrap, aggiornamento e policy IAM
.github/workflows/   build, push su ECR e aggiornamento dell'istanza via SSM
```

Il modello dei dati è semplice: un `User` ha molte `Receipt`, ognuna con molte `Purchase`, e ogni
`Purchase` punta a un `Product` del catalogo. Gli importi sono `BigDecimal` e le date `Instant`
serializzate in ISO-8601 UTC.

## Avvio in locale

Servono **JDK 21** e **Docker**. Il wrapper Gradle è nel repository, non serve installare Gradle.

### 1. Solo il backend (il modo comodo per sviluppare)

```bash
docker compose up -d db
```

```bash
./gradlew bootRun
```

Postgres resta esposto sulla `5433`, il backend risponde su <http://localhost:8080> senza prefisso
di percorso. Lo schema lo crea Hibernate (`ddl-auto=update`), quindi al primo avvio non c'è nulla
da migrare a mano.

Senza credenziali AWS il backend parte lo stesso: falliranno solo le chiamate che toccano S3
(caricamento e download dei PDF). Senza `OPENAI_API_KEY`, `/chat` risponde `503` e il resto
funziona normalmente.

### 2. Lo stack intero, come in produzione

```bash
cp .env.example .env
```

Nel `.env` vanno riempite almeno `POSTGRES_PASSWORD` e `AWS_S3_BUCKET`: senza, compose si ferma
subito invece di partire con valori sbagliati.

```bash
docker build -t receipthub-backend:local .
```

```bash
docker compose -f compose.prod.yaml -f compose.local.yaml up -d
```

Il sito arriva su <http://localhost>, con Caddy che smista `/api/*` al backend e tutto il resto al
frontend. L'immagine del frontend va costruita dal suo repository passando
`VITE_API_BASE_URL=http://localhost/api` come build-arg. `compose.local.yaml` monta `~/.aws` nel
container in sola lettura, così S3 funziona anche fuori da EC2.

Per popolare il database con dati dimostrativi:

```bash
docker compose -f compose.prod.yaml exec -T db psql -U sdcc -d backendsdcc_dev < deploy/seed-demo.sql
```

Lo script è ripetibile e non tocca gli utenti reali se non per assegnare loro qualche ricevuta.

## Configurazione

Tutto passa dall'ambiente; i default in `application.properties` sono quelli dello sviluppo in
locale. Il `.env` vero non è versionato — si parte da `.env.example`.

| Variabile | Default | A cosa serve |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/backendsdcc_dev` | Connessione a Postgres |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `sdcc` / `sdcc_pass` | Credenziali del database |
| `SERVER_CONTEXT_PATH` | *(vuoto)* | `/api` in produzione, per convivere col frontend sulla stessa origine |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origini ammesse, separate da virgola |
| `FIREBASE_PROJECT_ID` | `sdcc-a2df9` | Emittente e audience attesi nel token |
| `AWS_S3_BUCKET` | *(nessuno)* | Bucket dei PDF. Il nome contiene l'ID dell'account AWS, quindi non ha un default reale nel repository: senza, le sole chiamate a S3 falliscono |
| `AWS_S3_REGION` | `eu-north-1` | Regione del bucket |
| `OPENAI_API_KEY` | `missing` | Chiave del chatbot; se manca, `/chat` risponde 503 |
| `OPENAI_MODEL` | `gpt-5-mini` | Modello usato |
| `CHAT_MAX_DOMANDE_GIORNALIERE` | `30` | Tetto giornaliero di domande per utente |
| `LOG_LEVEL_SECURITY` | `INFO` | A `DEBUG` stampa i dettagli dei token: solo in locale |

Le credenziali AWS **non** sono fra queste: le risolve `DefaultCredentialsProvider` (ruolo IAM su
EC2, `~/.aws` in locale).

## API

Tutti i percorsi sono relativi alla radice in locale e a `/api` in produzione. Serve sempre un
token Firebase valido nell'header `Authorization: Bearer <token>`.

### Ricevute — `/receipt`

| Metodo | Percorso | Ruolo | Descrizione |
|---|---|---|---|
| `GET` | `/{code}` | USER, ADMIN | Dettaglio di una ricevuta |
| `GET` | `/all/{date}` | USER, ADMIN | Le proprie ricevute (tutte, se ADMIN); `date=true` ordina per data, `false` per importo |
| `POST` | `/add` | USER, ADMIN | Crea una ricevuta e genera il PDF |
| `POST` | `/upload-pdf` | USER, ADMIN | Carica un PDF e ne estrae il contenuto (`multipart/form-data`) |
| `GET` | `/pdf/{code}` | USER, ADMIN | URL S3 firmato e a scadenza (`text/plain`) |
| `GET` | `/find-by-code-like/{code}?threshold=` | USER, ADMIN | Ricerca fuzzy per codice |
| `GET` | `/find-by-email-like/{userEmail}?threshold=` | ADMIN | Ricerca fuzzy per cliente |
| `GET` | `/find-by-amount?amountMin=&amountMax=` | USER, ADMIN | Ricerca per intervallo di importo |
| `GET` | `/find-by-card?card=` | USER, ADMIN | Ricerca per ultime quattro cifre della carta |
| `DELETE` | `/{code}` | USER, ADMIN | Elimina una ricevuta (e il PDF su S3) |

### Prodotti — `/product`

| Metodo | Percorso | Ruolo | Descrizione |
|---|---|---|---|
| `GET` | `/all` | USER, ADMIN | Catalogo completo |
| `GET` | `/{code}` | USER, ADMIN | Un prodotto |
| `GET` | `/find?query=&threshold=` | USER, ADMIN | Ricerca fuzzy nel catalogo |
| `POST` | `/add` | ADMIN | Aggiunge un prodotto |
| `DELETE` | `/{code}` | ADMIN | Rimuove un prodotto |
| `GET` | `/product-of-the-month/{userEmail}` | ADMIN | Prodotto più acquistato del mese da un cliente |
| `GET` | `/product-of-time-span/{userEmail}/{dateMin}/{dateMax}` | ADMIN | Lo stesso, su un periodo |

### Utenti — `/user`

| Metodo | Percorso | Ruolo | Descrizione |
|---|---|---|---|
| `POST` | `/register` | USER, ADMIN | Completa il profilo dopo la registrazione su Firebase |
| `GET` | `/page` | USER, ADMIN | Il proprio profilo |
| `PUT` | `/update` | USER, ADMIN | Aggiorna il proprio profilo |
| `PUT` | `/update-by-email?email=` | ADMIN | Aggiorna il profilo di un cliente |
| `GET` | `/product-of-the-month` | USER, ADMIN | Il proprio prodotto del mese |
| `GET` | `/product-of-time-span?dateMin=&dateMax=` | USER, ADMIN | Il proprio prodotto in un periodo |
| `GET` | `/list` | ADMIN | Elenco dei clienti |
| `GET` | `/find?query=&threshold=` | ADMIN | Ricerca fuzzy fra i clienti |

### Statistiche — `/admin/stats` *(solo ADMIN)*

Tutte prendono `dateMin` e `dateMax` come `Instant` ISO-8601.

| Percorso | Descrizione |
|---|---|
| `GET /revenue` | Fatturato giorno per giorno |
| `GET /top-products?limit=` | Prodotti più venduti per quantità |
| `GET /payment-methods` | Distribuzione fra i metodi di pagamento |
| `GET /top-users?limit=` | Clienti che hanno speso di più |
| `GET /summary` | Riepilogo aggregato del periodo |

### Chat — `/chat`

| Metodo | Percorso | Ruolo | Descrizione |
|---|---|---|---|
| `POST` | `/chat` | USER, ADMIN | Domanda in linguaggio naturale, con la cronologia recente |
| `GET` | `/chat/status` | USER, ADMIN | `true` se il chatbot è configurato su questo ambiente |

### Convenzioni

- Una collezione vuota è un `200` con `[]`, non un `404`.
- Gli errori applicativi passano da `GlobalExceptionHandler` e dalle eccezioni in
  `support/exceptions`: `400` richiesta non valida, `404` non trovato, `409` conflitto,
  `429` troppe domande al chatbot.
- Nessuna entità JPA attraversa il confine HTTP: si passa sempre da un DTO.

## Autenticazione e ruoli

Le password non arrivano mai al backend. Il flusso è:

1. Il frontend autentica l'utente su **Firebase Authentication** e riceve un ID token.
2. Ogni chiamata all'API porta quel token come `Bearer`.
3. Spring Security lo valida come JWT: firma tramite le chiavi pubbliche di Google, emittente
   `securetoken.google.com/<project-id>` e audience uguale al project id
   (`FirebaseAudienceValidator`).
4. Il ruolo **non** viene dal token: `JwtAuthenticationConverter` risolve il `firebase_uid` sul
   database e legge lì il ruolo dell'utente. Un token valido di un utente sconosciuto vale
   `ROLE_USER`, mai amministratore.

I permessi sono dichiarati con `@PreAuthorize` sui singoli metodi, così la regola sta accanto al
codice che protegge invece che in un unico elenco lontano. La sessione è `STATELESS` e il CSRF
disattivato, coerentemente con un'API a token.

## Sicurezza

### Il trasporto: tre salti, non uno

Vale la pena distinguerli, perché "il frontend parla col backend in HTTP" è vero solo per uno dei
tre, ed è quello che non lascia mai la macchina.

| Salto | Protocollo | Note |
|---|---|---|
| Browser → Caddy | **HTTPS** | Certificato Let's Encrypt, richiesto e rinnovato da Caddy. Chi arriva su HTTP riceve un `308` verso HTTPS |
| Frontend ↔ Backend | **HTTPS** | Stessa origine: le chiamate partono dal browser verso `/api/*` e viaggiano nel canale TLS del salto precedente |
| Caddy → container | HTTP | Rete bridge interna alla singola istanza |

Il secondo salto merita una precisazione, perché è quello che di solito si fraintende: **il
frontend non chiama mai il backend da server**. Non c'è un solo `loader` né una `action` in tutta
l'applicazione — il rendering lato server produce la pagina, ma ogni chiamata all'API parte dal
browser dell'utente, con il token Firebase nell'header. Non esiste quindi una connessione
frontend→backend separata che possa essere in chiaro: il token viaggia sempre dentro TLS.

Il terzo salto è cifratura terminata al bordo (*TLS termination at the edge*), lo stesso schema di
un Application Load Balancer o di nginx davanti a un'applicazione. Il traffico fra Caddy e i
container resta dentro una rete bridge Docker sulla stessa EC2: non attraversa la rete, non esce
dall'istanza. Nel `compose.prod.yaml` backend e frontend dichiarano `expose` e non `ports`, quindi
le loro porte non sono pubblicate nemmeno sull'host.

Per intercettare quel salto bisognerebbe già essere root sulla macchina — e chi lo è ha anche il
`.env` con la password di Postgres, la chiave OpenAI e la memoria del processo Java. Cifrare fra i
container proteggerebbe da un attaccante che ha già vinto, al prezzo di gestire certificati
interni. In un deploy multi-host, dove il traffico attraversasse davvero il VPC, la conclusione
sarebbe opposta.

### HSTS

Il redirect da HTTP a HTTPS non basta da solo: chi digita `receipthub.duckdns.org` senza schema
manda comunque la prima richiesta in chiaro, e un attaccante sulla stessa rete può intercettarla e
tenere la vittima su HTTP senza che il redirect scatti mai (*sslstrip*). L'header

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

chiude la finestra: dopo una visita riuscita in HTTPS, il browser si rifiuta di usare HTTP su quel
dominio per un anno. È impostato nel `Caddyfile`. Manca volutamente `preload`: nella lista
precaricata dei browser si entra in un attimo e si esce dopo mesi.

### Superficie esposta

- **Security group**: in ingresso solo `80` e `443`. La `22` è chiusa e non esiste coppia di
  chiavi SSH; l'accesso alla macchina passa da AWS Session Manager.
- **Postgres** non è raggiungibile dall'esterno: nessuna porta pubblicata, vive sulla rete interna
  di Compose.
- **I PDF** non sono serviti da URL pubblici ma da URL S3 firmati e a scadenza, generati su
  richiesta di un utente autenticato.

### Gestione dei segreti

- **Nessuna chiave statica AWS in tutta la catena.** L'istanza usa il proprio ruolo IAM, GitHub
  Actions assume un ruolo via OIDC. Non ci sono credenziali da ruotare né da revocare.
- **La password di Postgres è generata sull'istanza al primo avvio** e non esce da lì: non passa
  da S3, non passa da GitHub, non è mai stata scritta in un file del repository.
- **`OPENAI_API_KEY` sta nel `.env` dell'istanza**, non fra i secret di GitHub: la pipeline non
  trasporta segreti per costruzione.
- **Le `VITE_*` del frontend non sono segrete** e stanno fra le *variables* di GitHub, non fra i
  *secrets*: Vite le incorpora nel bundle servito al browser. La chiave web di Firebase è pubblica
  per progetto — a proteggere l'account sono gli *authorized domains* e le regole.
- **Niente segreti in `aws ssm send-command`**: il testo dei comandi resta nella cronologia SSM e
  in CloudTrail. Per modificare il `.env` si usa una shell interattiva.

## Il chatbot (RiceVito)

`SmartQueryService` **non genera SQL**. Manda la domanda a OpenAI insieme agli strumenti dichiarati
in `ReceiptChatTools`, e lascia scegliere al modello quale chiamare: ogni strumento passa dai
service che usa già il resto dell'applicazione, quindi eredita gli stessi controlli di ruolo. Un
cliente non può farsi raccontare le ricevute di un altro nemmeno chiedendolo bene.

Le difese sono tre, tutte volute:

- **Ambito.** Il prompt di sistema limita le risposte a ricevute, prodotti, statistiche e
  navigazione dell'applicazione.
- **Costo.** Solo gli ultimi scambi vengono rimandati indietro, con un tetto sulla dimensione
  complessiva: la cronologia la compone il client, quindi contare i messaggi non basterebbe. La
  parte fissa del prompt sta all'inizio per sfruttare la cache dei prefissi di OpenAI.
- **Abuso.** `ChatRateLimiter` limita le domande giornaliere per utente
  (`CHAT_MAX_DOMANDE_GIORNALIERE`, 30 di default) e oltre il tetto risponde `429`. Il conteggio è
  in memoria e per istanza: sufficiente con una sola istanza, da spostare su Redis se si scalasse
  in orizzontale.

## Deploy

Il percorso completo — budget alert, ruoli IAM, istanza EC2, ECR, OIDC per GitHub Actions, dominio
e HTTPS — è documentato passo per passo in **[`DEPLOY.md`](DEPLOY.md)**.

In breve: a ogni push su `master`, GitHub Actions assume un ruolo AWS via OIDC (nessuna chiave
statica nei secrets), costruisce l'immagine, la pubblica su ECR e chiede all'istanza via **SSM** di
riscaricarla e riavviare il servizio. Sull'istanza non si entra in SSH: la porta 22 è chiusa e la
shell passa da Session Manager.

## Stato e limiti noti

- **Test.** C'è il solo smoke test del contesto Spring (`BackendSdccApplicationTests`). È la
  lacuna più evidente del progetto: la verifica è stata fatta a mano sull'ambiente reale, non da
  una suite automatica.
- **Scala.** Una sola istanza, quindi nessuna replica e nessun bilanciatore. Il codice però è
  stateless e non tiene niente in memoria tranne il contatore della chat, quindi la scalatura
  orizzontale è più una questione di infrastruttura che di riscrittura.
- **Schema.** `ddl-auto=update` va bene per un progetto didattico; un sistema vero userebbe
  migrazioni versionate (Flyway o Liquibase).
- **Costi.** L'istanza viene spenta a mano quando non serve, e non c'è un Elastic IP: il record
  DuckDNS viene riallineato all'avvio (`deploy/duckdns-update.sh`).
