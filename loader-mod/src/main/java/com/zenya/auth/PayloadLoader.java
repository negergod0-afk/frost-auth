package com.zenya.auth;

/**
 * Stub implementation of PayloadLoader to allow compilation.
 */
public final class PayloadLoader {
    private static final PayloadLoader INSTANCE = new PayloadLoader();
    
    private PayloadLoader() {}
    
    public static PayloadLoader getInstance() {
        return INSTANCE;
    }
    
    public Object instantiate(String fqcn) throws Exception {
        throw new ClassNotFoundException("Payload modules are disabled in this build: " + fqcn);
    }
    
    public void clearCache() {
        // stub
    }
}
