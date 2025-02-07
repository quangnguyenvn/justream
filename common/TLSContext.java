package justream.common;

import java.io.InputStream;
import java.io.FileInputStream;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.*;

public class TLSContext {
    
    private boolean useClientMode;

    private KeyStore keyStore;
    
    private SSLContext context;
    
    private TLSWorkers workers;
    
    public TLSContext(InputStream keyStoreIS, String keyStoreType, String keyStorePWD, 
		      boolean useClientMode, int numWorkers) {
		
	try {
	    initKeyStore(keyStoreIS, keyStoreType, keyStorePWD);
	    initSSLContext(keyStorePWD);
	    this.useClientMode = useClientMode;
	    this.workers = new TLSWorkers(numWorkers);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.out.println("FATAL: " + e.getMessage());
	    System.exit(0);
	}
    }
    
    public static final TLSBuffer createTLSBuffer() {
	return new TLSBuffer(Util.UNIT_SIZE << 6);
    }

    private void initKeyStore(InputStream is, String type, String pwd) throws Exception {
	keyStore = KeyStore.getInstance(type);
	keyStore.load(is, pwd.toCharArray());
    }

    private void initSSLContext(String pwd) throws Exception {
	KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	keyManagerFactory.init(keyStore, pwd.toCharArray());

	TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	trustManagerFactory.init(keyStore);
	
	context = SSLContext.getInstance("TLS");
	context.init(keyManagerFactory.getKeyManagers(),
		     trustManagerFactory.getTrustManagers(),
		     new SecureRandom());
    }

    public final TLSEngine createTLSEngine() throws Exception {
	TLSEngine engine = new TLSEngine(context.createSSLEngine(), workers.getRRQueue());
	engine.setUseClientMode(useClientMode);
	return engine;
    }
}