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
package io.github.bynix.vine.utils;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author bynix
 */
public class GenCert {

    public static void main(String[] args) throws Exception {
        GenCert gen = new GenCert();
        KeyPair keyPair = gen.genKeyPair(1024);
        String subject = "O=https://github.com/bynix, OU=Vine CA, CN=Vine ROOT CA";
        LocalDate caNotBefore = LocalDate.of(2021, 9, 1);
        LocalDate caNotAfter = caNotBefore.plusYears(100);
        X509Certificate cert = gen.genCert(subject, subject,
            Date.from(caNotBefore.atStartOfDay().toInstant(ZoneOffset.UTC)),
            Date.from(caNotAfter.atStartOfDay().toInstant(ZoneOffset.UTC)),
            keyPair);

        String rootDir = System.getProperty("user.dir");
        String derPath = "vine-core/src/main/resources/io/github/bynix/vine/http/mitm/cert/VINE_CERT.DER";
        String keyPath = "vine-core/src/main/resources/io/github/bynix/vine/http/mitm/cert/PRIVATE.KEY";
        gen.toFile(cert.getEncoded(), new File(rootDir, derPath), caNotBefore);
        gen.toFile(keyPair.getPrivate().getEncoded(), new File(rootDir, keyPath), caNotBefore);
    }

    public KeyPair genKeyPair(int size) throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(size, new SecureRandom());
        return keyPairGen.genKeyPair();
    }

    public X509Certificate genCert(String issuer, String subject, Date caNotBefore, Date caNotAfter, KeyPair keyPair) throws Exception {
        JcaX509v3CertificateBuilder jv3Builder = new JcaX509v3CertificateBuilder(new X500Name(issuer),
            BigInteger.valueOf(ThreadLocalRandom.current().nextLong(Integer.MAX_VALUE, Long.MAX_VALUE)),
            caNotBefore,
            caNotAfter,
            new X500Name(subject),
            keyPair.getPublic());
        jv3Builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(jv3Builder.build(signer));
    }

    public void toFile(byte[] bytes, File file, LocalDate date) throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        Path path = file.toPath();
        Files.write(file.toPath(), bytes);

        FileTime fileTime = FileTime.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
        BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);
        view.setTimes(fileTime, fileTime, fileTime);
    }

}
