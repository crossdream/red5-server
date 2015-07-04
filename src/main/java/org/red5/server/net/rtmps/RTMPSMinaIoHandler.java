/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
 * 
 * Copyright 2006-2015 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.net.rtmps;

import java.io.File;
import java.io.NotActiveException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;

import javax.net.ssl.SSLContext;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.KeyStoreFactory;
import org.apache.mina.filter.ssl.SslContextFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.filter.ssl.SslFilter.SslFilterMessage;
import org.red5.server.net.rtmp.RTMPMinaIoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Native RTMPS protocol events fired by the MINA framework.
 * 
 * <pre>
 * var nc:NetConnection = new NetConnection();
 * nc.proxyType = "best";
 * nc.connect("rtmps:\\localhost\app");
 * </pre>
 * 
 * Originally created by: Kevin Green
 * 
 * http://tomcat.apache.org/tomcat-6.0-doc/ssl-howto.html http://java.sun.com/j2se/1.5.0/docs/guide/security/CryptoSpec.html#AppA http://java.sun.com/j2se/1.5.0/docs/api/java/security/KeyStore.html http://tomcat.apache.org/tomcat-3.3-doc/tomcat-ssl-howto.html
 * 
 * Unexpected exception from SSLEngine.closeInbound() https://issues.apache.org/jira/browse/DIRMINA-272
 * 
 * @author Kevin Green (kevygreen@gmail.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPSMinaIoHandler extends RTMPMinaIoHandler {

	private static Logger log = LoggerFactory.getLogger(RTMPSMinaIoHandler.class);

	/**
	 * Password for accessing the keystore.
	 */
	private String keystorePassword;

	/**
	 * Password for accessing the truststore.
	 */
	private String truststorePassword;

	/**
	 * Stores the keystore path.
	 */
	private String keystoreFile;

	/**
	 * Stores the truststore path.
	 */
	private String truststoreFile;

	/**
	 * Names of the SSL cipher suites which are currently enabled for use.
	 */
	private String[] cipherSuites;

	/**
	 * Names of the protocol versions which are currently enabled for use.
	 */
	private String[] protocols;

	/**
	 * Use client (or server) mode when handshaking.
	 */
	private boolean useClientMode;

	/**
	 * Request the need of client authentication.
	 */
	private boolean needClientAuth;

	/**
	 * Indicates that we would like to authenticate the client but if client certificates are self-signed or have no certificate chain then we are still good
	 */
	private boolean wantClientAuth;

	static {
		if (log.isDebugEnabled()) {
			Provider[] providers = Security.getProviders();
			for (Provider provider : providers) {
				log.debug("Provider: {} = {}", provider.getName(), provider.getInfo());
			}
		}
	}

	@Override
	public void sessionOpened(IoSession session) throws Exception {
		if (keystoreFile == null || truststoreFile == null) {
			throw new NotActiveException("Keystore or truststore are null");
		}
		// create the ssl filter
		SSLContext context = getSslContext();
		// create the ssl filter using server mode
		SslFilter sslFilter = new SslFilter(context);
		sslFilter.setUseClientMode(useClientMode);
		sslFilter.setNeedClientAuth(needClientAuth);
		sslFilter.setWantClientAuth(wantClientAuth);
		if (cipherSuites != null) {
			sslFilter.setEnabledCipherSuites(cipherSuites);
		}
		if (protocols != null) {
			sslFilter.setEnabledProtocols(protocols);
		}
		// add rtmpe filter after ssl
		session.getFilterChain().addBefore("rtmpeFilter", "sslFilter", sslFilter);
		session.setAttribute(SslFilter.USE_NOTIFICATION, Boolean.TRUE);
		log.debug("isSslStarted:", sslFilter.isSslStarted(session));		
		super.sessionOpened(session);
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		if (message instanceof SslFilterMessage) {
			log.info(message.toString());
		} else {
			super.messageReceived(session, message);
		}
	}

	private SSLContext getSslContext() {
		SSLContext sslContext = null;
		try {
			File keyStore = new File(keystoreFile);
			File trustStore = new File(truststoreFile);
			if (keyStore.exists() && trustStore.exists()) {
				final KeyStoreFactory keyStoreFactory = new KeyStoreFactory();
				keyStoreFactory.setDataFile(keyStore);
				keyStoreFactory.setPassword(keystorePassword);

				final KeyStoreFactory trustStoreFactory = new KeyStoreFactory();
				trustStoreFactory.setDataFile(trustStore);
				trustStoreFactory.setPassword(truststorePassword);

				final SslContextFactory sslContextFactory = new SslContextFactory();
				sslContextFactory.setProtocol("TLS");

				final KeyStore ks = keyStoreFactory.newInstance();
				sslContextFactory.setKeyManagerFactoryKeyStore(ks);

				final KeyStore ts = trustStoreFactory.newInstance();
				sslContextFactory.setTrustManagerFactoryKeyStore(ts);
				sslContextFactory.setKeyManagerFactoryKeyStorePassword(keystorePassword);

				sslContext = sslContextFactory.newInstance();
				log.debug("SSL provider is: {}", sslContext.getProvider());
			} else {
				log.warn("Keystore or Truststore file does not exist");
			}
		} catch (Exception ex) {
			log.error("Exception getting SSL context", ex);
		}
		return sslContext;
	}

	/**
	 * Password used to access the keystore file.
	 * 
	 * @param password
	 *            keystore password
	 */
	public void setKeystorePassword(String password) {
		this.keystorePassword = password;
	}

	/**
	 * Password used to access the truststore file.
	 * 
	 * @param password
	 *            truststore password
	 */
	public void setTruststorePassword(String password) {
		this.truststorePassword = password;
	}

	/**
	 * Set keystore data from a file.
	 * 
	 * @param path
	 *            contains keystore
	 */
	public void setKeystoreFile(String path) {
		this.keystoreFile = path;
	}

	/**
	 * Set truststore file path.
	 * 
	 * @param path
	 *            contains truststore
	 */
	public void setTruststoreFile(String path) {
		this.truststoreFile = path;
	}

	public String[] getCipherSuites() {
		return cipherSuites;
	}

	public void setCipherSuites(String[] cipherSuites) {
		this.cipherSuites = cipherSuites;
	}

	public String[] getProtocols() {
		return protocols;
	}

	public void setProtocols(String[] protocols) {
		this.protocols = protocols;
	}

	public void setUseClientMode(boolean useClientMode) {
		this.useClientMode = useClientMode;
	}

	public void setNeedClientAuth(boolean needClientAuth) {
		this.needClientAuth = needClientAuth;
	}

	public void setWantClientAuth(boolean wantClientAuth) {
		this.wantClientAuth = wantClientAuth;
	}

}
