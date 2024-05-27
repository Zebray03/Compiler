import java.util.LinkedHashMap;
import java.util.Map;

public class LocalScope implements Scope {
    private String name;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Scope enclosingScope;
    private static int localScopeCounter = 0;

    public LocalScope(Scope enclosingScope) {
        this.name = "LocalScope";
        this.enclosingScope = enclosingScope;
        String localScopeName = getName() + localScopeCounter;
        setName(localScopeName);
        localScopeCounter++;
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