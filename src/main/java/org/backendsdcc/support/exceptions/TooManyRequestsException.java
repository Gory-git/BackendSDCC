// exceptions/TooManyRequestsException.java
package org.backendsdcc.support.exceptions;

/**
 * L'utente ha superato un limite d'uso (per ora solo le domande al chatbot).
 * Diventa un 429: non e' un errore della richiesta, e' la stessa richiesta
 * fatta troppe volte, e il client puo' riprovare piu' tardi.
 */
public class TooManyRequestsException extends RuntimeException
{
    public TooManyRequestsException(String message) { super(message); }
}
