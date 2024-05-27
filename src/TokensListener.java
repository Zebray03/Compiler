import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

class TokensListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        msg = String.valueOf(msg.charAt(29));
        System.err.println("Error type A at Line " + line + ": Mysterious character \"" + msg + "\".");
        throw e;
    }
}
