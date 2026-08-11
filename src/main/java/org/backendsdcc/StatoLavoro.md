# Backend SDCC

I clienti possono inserire sulla piattaforma le ricevute dei loro acquisti, che vengono poi processate e analizzate per fornire statistiche e informazioni utili. 
Gli utenti possono essere di due tipi: utenti base, che caricano le ricevute, e utenti amministratori, che possono gestire i prodotti e le ricevute.
Gli utenti possono creare una ricevuta sulla piattaforma, inserendo i dettagli dell'acquisto, come il prodotto acquistato, il metodo di pagamento utilizzato e l'importo speso, scaricandola in seguito come PDF oppure caricare direttamente il PDF della ricevuta. Il sistema estrae automaticamente le informazioni rilevanti dal PDF e le memorizza nel database.
Ci sarà un chatbot che aiuterà gli utenti a navigare sulla piattaforma e a risolvere eventuali problemi. Il chatbot sarà in grado di rispondere alle domande degli utenti, fornire informazioni sui prodotti e sulle ricevute, e guidare gli utenti attraverso il processo di caricamento delle ricevute.
Ci saranno delle statistiche per gli utenti base, che potranno visualizzare le informazioni sui loro acquisti, come il totale speso, la media degli importi spesi e la distribuzione degli acquisti per prodotto, prodotto più acquistato ecc. Gli utenti amministratori avranno accesso a statistiche più avanzate, che consentiranno loro di analizzare i dati delle ricevute caricate dagli utenti e di identificare eventuali tendenze o problemi.
Pensavo di aggiungerlo ma non credo serva un sistema di riconoscimento delle ricevute mal formate. È inutile siccome un utente che carica una vecchia ricevuta può caricarla manualmente senza problemi.
Valutare sistemi di deploy e scalabilità su AWS, sia per l'infrastruttura che per il database. Valutare anche sistemi di caching per migliorare le performance.
Viste le ultime modifiche al sistema (BigDecimal e Instant) valutare anche se necessario aggiungere dimensioni massime per i campi dei modelli.
Valutare l'aggiunta di eccezioni custom o lasciare le eccezioni standard.

## Models

### PaymentMethod

Trasformato in una enum, potrebbe non essere più necessario avere un DTO per questo modello.

### Product

Tutto come prima

### Purchase

Cambiati i campi float in bigdecimal. Sistemata algebra relazionale.

### Receipt

Cambiati i campi float in bigdecimal e date in Instant. Sistemata algebra relazionale.

### User

Tutto come prima. Da non dimenticare che esisteranno due tipologie di utenti: utenti base, che caricano le ricevute, e utenti amministratori, che possono gestire i prodotti e le ricevute. Un utente base vede soltanto le sue statistiche, mentre un utente amministratore può vedere le statistiche di tutti gli utenti.

## Repositories
    
### PaymentMethodRepository

Non serve mi sa

### ProductRepository

Da vedere se va sistemato

### PurchaseRepository

Da vedere se va sistemato

### ReceiptRepository

Sistemati i campi float in bigdecimal e date in Instant.

### UserRepository

Da vedere se va sistemato

## Services

### PDFService

fatti molti fix. Resta da salvare i PDF in un DB. Vedere come si fa con S3 AWS. Da vedere se serve un DTO per i PDF.

### ProductService

Da finire e da controllare

### ReceiptService

Da finire e da controllare
Aggiunto salvataggio dei PDF in S3 AWS. 

### UserService

Da iniziare

### SmartQueryService

Da iniziare

## Controllers

Non ancora iniziati, prima finisco i servizi e poi passo ai controller.

### PaymentMethodController

### ProductController

### PDFController   

### PurchaseController

### ReceiptController

### UserController

## Support

### Comparators

#### ReceiptAmountComparator

Sistemato per bigdecimal. Comodo per ordinare le ricevute in base all'importo speso.

#### ReceiptDateComparator

Sistemato per Instant. Comodo per ordinare le ricevute in base alla data di acquisto.

### DTO

#### PaymentMethodDTO

Non credo serva più, visto che PaymentMethod è diventata una enum.

#### ProductDTO

Sistemato

#### PurchaseDTO

Da sistemare, non c'è validazione

#### ReceiptDTO

Da sistemare, non c'è validazione

#### UserDTO

Da sistemare, non c'è validazione

### Messages

#### ResponseMessage

Response message classico, vedere se va bene e in caso cambiarlo

### PDF

#### PDF

Base di creazione dei PDF, valutarlo globalmente e in caso sistemare. Da completare

### Validators

#### DateValidator

Sistemato ma comunque da vedere.