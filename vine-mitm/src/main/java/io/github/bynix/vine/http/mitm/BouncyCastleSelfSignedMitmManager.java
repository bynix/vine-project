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
import io.netty.util.internal.ThreadLocalRandom;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.CertRuntimeException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bynix
 */
public class BouncyCastleSelfSignedMitmManager extends SelfSignedMitmManager {

    private Date notBefore;
    private Date notAfter;
    private X500Name issuer;
    private KeyPair keyPair;

    int cnIndex;

    Map<String, SslContext> sslContextCache = new ConcurrentHashMap<>();

    public BouncyCastleSelfSignedMitmManager() throws Exception {
        super();
        init();
    }

    public BouncyCastleSelfSignedMitmManager(InputStream certInputStream, InputStream keyInputStream) throws Exception {
        super(certInputStream, keyInputStream);
        init();
    }

    public void init() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(1024, new SecureRandom());
        this.keyPair = keyPairGen.genKeyPair();
        this.notBefore = issuerCertificate.getNotBefore();
        this.notAfter = issuerCertificate.getNotAfter();
        this.issuer = X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded());
        this.cnIndex = getCnIndex0();
    }

    @Override
    public SslContext serverSslContext(String host) throws Exception {
        return sslContextCache.computeIfAbsent(host, key -> {
            try {
                X509Certificate cert = generateServerCert(host);
                return SslContextBuilder.forServer(keyPair.getPrivate(), cert).build();
            } catch (Exception e) {
                throw new CertRuntimeException("Failed to generate server certificate.", e);
            }
        });
    }

    public X509Certificate generateServerCert(String host) throws Exception {
        BigInteger serial = BigInteger.valueOf(ThreadLocalRandom.current().nextLong(Integer.MAX_VALUE, Long.MAX_VALUE));
        X500Name subject = generateSubject(host);
        PublicKey publicKey = keyPair.getPublic();
        JcaX509v3CertificateBuilder jcaX509v3CertBuilder = new JcaX509v3CertificateBuilder(issuer,
            serial, notBefore, notAfter, subject, publicKey);

        GeneralNames subjectAltName = new GeneralNames(new GeneralName(GeneralName.dNSName, host));
        jcaX509v3CertBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(issuerPrivateKey);
        return new JcaX509CertificateConverter().getCertificate(jcaX509v3CertBuilder.build(signer));
    }

    private int getCnIndex0() {
        RDN[] arr = this.issuer.getRDNs();
        for (int i = 0; i < arr.length; i++) {
            AttributeTypeAndValue attribute = arr[i].getFirst();
            ASN1ObjectIdentifier type = attribute.getType();
            if (BCStyle.CN.equals(type)) {
                return i;
            }
        }
        return -1;
    }

    public X500Name generateSubject(String host) {
        RDN[] arr = this.issuer.getRDNs();
        AttributeTypeAndValue attribute = new AttributeTypeAndValue(BCStyle.CN, new DERPrintableString(host));
        if (cnIndex == -1) {
            int length = arr.length;
            arr = Arrays.copyOf(arr, length + 1);
            arr[length] = new RDN(attribute);
        } else {
            arr[cnIndex] = new RDN(attribute);
        }
        return new X500Name(arr);
    }

    /*
    #####################################################################################
    ################################## Getter | Setter ##################################
    #####################################################################################
     */

    public Date getNotBefore() {
        return notBefore;
    }

    public BouncyCastleSelfSignedMitmManager setNotBefore(Date notBefore) {
        this.notBefore = notBefore;
        return this;
    }

    public Date getNotAfter() {
        return notAfter;
    }

    public BouncyCastleSelfSignedMitmManager setNotAfter(Date notAfter) {
        this.notAfter = notAfter;
        return this;
    }

    public X500Name getIssuer() {
        return issuer;
    }

    public BouncyCastleSelfSignedMitmManager setIssuer(X500Name issuer) {
        this.issuer = issuer;
        this.cnIndex = getCnIndex0();
        return this;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public BouncyCastleSelfSignedMitmManager setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
        return this;
    }

    public BouncyCastleSelfSignedMitmManager setCnIndex(int cnIndex) {
        this.cnIndex = cnIndex;
        return this;
    }

    public int getCnIndex() {
        return cnIndex;
    }
}
