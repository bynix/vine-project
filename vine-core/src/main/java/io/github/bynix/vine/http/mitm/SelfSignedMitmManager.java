/*
  Copyright 2021 The Vine Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.bynix.vine.http.mitm;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * @author bynix
 */
public class SelfSignedMitmManager implements MitmManager {

    protected PrivateKey issuerPrivateKey;
    protected X509Certificate issuerCertificate;

    private SslContext defaultServerSslContext;

    public SelfSignedMitmManager() throws Exception {
        loadRootCertificate();
        loadDefaultServerSslContext();
    }

    public SelfSignedMitmManager(InputStream certInputStream, InputStream keyInputStream) throws Exception {
        loadRootCertificate(certInputStream, keyInputStream);
        loadDefaultServerSslContext();
    }


    @Override
    public SslContext serverSslContext(String host) throws Exception {
        return defaultServerSslContext;
    }

    @Override
    public X509Certificate getIssuerCertificate() {
        return issuerCertificate;
    }

    private void loadDefaultServerSslContext() throws SSLException {
        this.defaultServerSslContext = SslContextBuilder.forServer(issuerPrivateKey, issuerCertificate).build();
    }

    private void loadRootCertificate(InputStream certInputStream, InputStream keyInputStream) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        this.issuerCertificate = (X509Certificate) cf.generateCertificate(certInputStream);

        byte[] bytes = new byte[keyInputStream.available()];
        keyInputStream.read(bytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytes);
        this.issuerPrivateKey = keyFactory.generatePrivate(privateKeySpec);
    }

    private void loadRootCertificate() throws Exception {
        ClassLoader cl = this.getClass().getClassLoader();
        try (InputStream certInputStream = cl.getResourceAsStream("io/github/bynix/vine/http/mitm/cert/VINE_CERT.DER");
             InputStream keyInputStream = cl.getResourceAsStream("io/github/bynix/vine/http/mitm/cert/PRIVATE.KEY")) {
            loadRootCertificate(certInputStream, keyInputStream);
        }
    }

}
