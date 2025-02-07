package justream.tls;

import java.io.InputStream;
import java.io.FileInputStream;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.*;

public class TLSContext {
    
    private KeyStore keyStore;
    
    private SSLContext context;
    
    private Worker worker;

    public TLSContext(InputStream keyStoreIS, String keyStoreType, String keyStorePWD) {
	
	worker = new Worker();

	try {
	    initKeyStore(keyStoreIS, keyStoreType, keyStorePWD);
	    initSSLContext(keyStorePWD);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.out.println("FATAL: " + e.getMessage());
	    System.exit(0);
	}
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
	return new TLSEngine(context.createSSLEngine(), worker.getQueue(), worker.getWorkingBuffer());
    }
}