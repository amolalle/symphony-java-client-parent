package com.github.deutschebank.symphonyp.maven;

import com.symphony.api.bindings.ApiBuilder;
import com.symphony.api.bindings.jersey.JerseyApiBuilder;
import com.symphony.api.id.SymphonyIdentity;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServerErrorException;
import org.slf4j.Logger;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

public class ProxyingWrapper {
	
    private static final String DEFAULT_JKS_RESOURCE = "all_symphony_certs_truststore.jks";

    private final List<String> proxyHosts;

    private final Logger log;
    private final JerseyApiBuilder builder;
    private final String url;

    public ProxyingWrapper(String jksFile, List<String> proxyHosts, String url, SymphonyIdentity id, Logger log) throws IOException {
        super();
        this.proxyHosts = new ArrayList<String>(proxyHosts);
        builder = new JerseyApiBuilder();
        this.log = log;
        this.url = url;

        builder.setKeyManagers(id.getKeyManagers());
        builder.setUrl(url);

        InputStream is = readJks(jksFile);

        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(is, null);
            tmf.init(keystore);
            builder.setTrustManagers(tmf.getTrustManagers());
        } catch (Exception e) {
            throw new RuntimeException("Couldn't instantiate trust store", e);
        }
    }

    interface DoIt<X> {


        X performFunction(ApiBuilder ab);

    }

    public <X> X performWithAndWithoutProxies(DoIt<X> d) {
        Exception last = null;
        for (String p : new ArrayList<>(proxyHosts)) {
            try {
                builder.setProxyDetails(p, null, null, 8080);
                return d.performFunction(builder);
            } catch (ProcessingException e) {
                if (e.getCause() instanceof ConnectException) {
                    log.debug("Couldn't connect to " + url + " with proxy " + p);
                    proxyHosts.remove(p);
                }
                last = e;
            } catch (ServerErrorException e) {
                log.debug("Couldn't connect to " + url + " with proxy " + p);
                proxyHosts.remove(p);
                last = e;
            } catch (ForbiddenException e) {
                // 403 from proxy
                log.debug("Couldn't connect to " + url + " with proxy " + p);
                proxyHosts.remove(p);
                last = e;
            }
        }

        throw new RuntimeException("Exhausted all proxy options", last);
    }

    private InputStream readJks(String jksFile) throws IOException {
        if (isNotBlank(jksFile)) {
            return new FileInputStream(jksFile);
        } else {
            log.info("trust-store.jks was not specified, using default jks");
            return this.getClass().getClassLoader().getResourceAsStream(DEFAULT_JKS_RESOURCE);
        }
    }
}
