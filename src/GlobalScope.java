import java.util.LinkedHashMap;
import java.util.Map;

public class GlobalScope implements Scope {
    private String name;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Scope enclosingScope;

    public GlobalScope(Scope enclosingScope) {
        this.name = "GlobalScope";
        this.enclosingScope = enclosingScope;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Scope getEnclosingScope() {
        return this.enclosingScope;
    }

    public Map<String, Symbol> getSymbols() {
        return this.symbols;
    }

    @Override
    public void define(Symbol symbol) {
        this.symbols.put(symbol.getName(), symbol);
    }

    @Override
    public Symbol resolve(String name) {
        Symbol symbol = this.symbols.get(name);
        if (symbol != null) {
            return symbol;
        } else if (this.enclosingScope != null) {
            return this.enclosingScope.resolve(name);
        } else {
            return null;
        }
    }
}