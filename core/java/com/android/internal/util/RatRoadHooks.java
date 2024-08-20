/*
 * Copyright (C) 2022 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *           (C) 2024 The LeafOS Project
 *           (C) 2024 Kusuma
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

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.Process;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.asn1.DERTaggedObject;
import com.android.internal.org.bouncycastle.asn1.x509.Extension;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import com.android.internal.org.bouncycastle.openssl.PEMKeyPair;
import com.android.internal.org.bouncycastle.openssl.PEMParser;
import com.android.internal.org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import com.android.internal.org.bouncycastle.operator.ContentSigner;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import com.android.internal.org.bouncycastle.util.io.pem.PemReader;
import com.android.internal.util.XMLParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class RatRoadHooks {

    private static final String TAG = RatRoadHooks.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean USE_RATROAD =
            SystemProperties.getBoolean("persist.sys.extra.use_rr", true);
    private static final boolean USE_ALLEYWAY =
            SystemProperties.getBoolean("persist.sys.extra.use_rr_aw", true);

    private static final String sStockFp =
            Resources.getSystem().getString(R.string.config_stockFingerprint);

    private static final String PROPS_FILE = "rr_props.json";
    private static final String KEYS_FILE = "rr_keys.xml";

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static volatile String sProcessName;
    private static volatile boolean sIsGms, sIsFinsky;

    private static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17");
    private static final CertificateFactory certificateFactory;
    private static final Map<String, KeyBox> keyboxes = new HashMap<>();

    static {
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (Throwable t) {
            Log.e(TAG, t.toString());
            throw new RuntimeException(t);
        }
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);

        /* Set certified properties for GMSCore
         * Set stock fingerprint for ARCore
         */
        if (USE_RATROAD) {
            if (packageName.equals(PACKAGE_GMS)) {
                dlog("Setting fresh build date for: " + packageName);
                setPropValue("TIME", String.valueOf(System.currentTimeMillis()));
                if (sIsGms) {
                    setCertifiedPropsForGms();
                }
            } else if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
                dlog("Setting stock fingerprint for: " + packageName);
                setPropValue("FINGERPRINT", sStockFp);
            }
        }
    }

    private static void setPropValue(String key, String value) {
        try {
            // Unlock
            Class clazz = Build.class;
            if (key.startsWith("VERSION:")) {
                clazz = Build.VERSION.class;
                key = key.substring(8);
            }
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            if (field.getType().equals(Long.TYPE)) {
                field.set(null, Long.parseLong(value));
            } else if (field.getType().equals(Integer.TYPE)) {
                field.set(null, Integer.parseInt(value));
            } else {
                field.set(null, value);
            }

            // Lock
            field.setAccessible(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static PEMKeyPair parseKeyPair(String key) throws Throwable {
        try (PEMParser parser = new PEMParser(new StringReader(key))) {
            return (PEMKeyPair) parser.readObject();
        }
    }

    private static Certificate parseCert(String cert) throws Throwable {
        try (PemReader reader = new PemReader(new StringReader(cert))) {
            return certificateFactory.generateCertificate(
                    new ByteArrayInputStream(reader.readPemObject().getContent()));
        }
    }

    private static void setCertifiedPropsForGms() {
        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        if (!was) {
            File propsFile = new File(Environment.getDataSystemDirectory(), PROPS_FILE);
            String savedProps = readFromFile(propsFile);
            if (TextUtils.isEmpty(savedProps)) {
                Log.e(TAG, "No props found to spoof");
                return;
            }
            dlog("Found props");
            try {
                JSONObject parsedProps = new JSONObject(savedProps);
                Iterator<String> keys = parsedProps.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = parsedProps.getString(key);
                    dlog(key + ": " + value);
                    setPropValue(key, value);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON data", e);
            }
        } else {
            dlog("Skip spoofing build for GMS, because GmsAddAccountActivityOnTop");
        }
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    private static String readFromFile(File file) {
        StringBuilder content = new StringBuilder();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from file", e);
            }
        }
        return content.toString();
    }

    private static void readFromXml(String data) {
        keyboxes.clear();
        if (data == null) {
            dlog("Clear all keyboxes");
            return;
        }
        XMLParser xmlParser = new XMLParser(data);

        try {
            int numberOfKeyboxes = Integer.parseInt(Objects.requireNonNull(xmlParser.obtainPath(
                    "AndroidAttestation.NumberOfKeyboxes").get("text")));
            for (int i = 0; i < numberOfKeyboxes; i++) {
                String keyboxAlgorithm = xmlParser.obtainPath(
                        "AndroidAttestation.Keybox.Key[" + i + "]").get("algorithm");
                String privateKey = xmlParser.obtainPath(
                        "AndroidAttestation.Keybox.Key[" + i + "].PrivateKey").get("text");
                int numberOfCertificates = Integer.parseInt(Objects.requireNonNull(xmlParser.obtainPath(
                        "AndroidAttestation.Keybox.Key[" + i + "].CertificateChain.NumberOfCertificates").get("text")));

                LinkedList<Certificate> certificateChain = new LinkedList<>();

                for (int j = 0; j < numberOfCertificates; j++) {
                    Map<String, String> certData = xmlParser.obtainPath(
                            "AndroidAttestation.Keybox.Key[" + i + "].CertificateChain.Certificate[" + j + "]");
                    certificateChain.add(parseCert(certData.get("text")));
                }
                String algo;
                if (keyboxAlgorithm.toLowerCase().equals("ecdsa")) {
                    algo = KeyProperties.KEY_ALGORITHM_EC;
                } else {
                    algo = KeyProperties.KEY_ALGORITHM_RSA;
                }
                var pemKp = parseKeyPair(privateKey);
                var kp = new JcaPEMKeyConverter().getKeyPair(pemKp);
                keyboxes.put(algo, new KeyBox(pemKp, kp, certificateChain));
            }
            dlog("update " + numberOfKeyboxes + " keyboxes");
        } catch (Throwable t) {
            Log.e("Error loading xml file (keyboxes cleared): ", t.toString());
        }
    }

    public static Certificate[] onEngineGetCertificateChain(Certificate[] caList) {
        File keysFile = new File(Environment.getDataSystemDirectory(), KEYS_FILE);
        String savedKeys = readFromFile(keysFile);
        if (USE_RATROAD && USE_ALLEYWAY
                && keysFile.exists() && savedKeys.contains("Keybox")) {
            try {
                readFromXml(savedKeys);
            } catch (Exception e) {
                Log.e("Failed to update keybox", e.toString());
            }

            try {
                X509Certificate leaf = (X509Certificate) certificateFactory.generateCertificate(
                        new ByteArrayInputStream(caList[0].getEncoded()));
                byte[] bytes = leaf.getExtensionValue(OID.getId());
                if (bytes == null) return caList;
    
                X509CertificateHolder leafHolder = new X509CertificateHolder(leaf.getEncoded());
                Extension ext = leafHolder.getExtension(OID);
                ASN1Sequence sequence = ASN1Sequence.getInstance(ext.getExtnValue().getOctets());
                ASN1Encodable[] encodables = sequence.toArray();
                ASN1Sequence teeEnforced = (ASN1Sequence) encodables[7];
                ASN1EncodableVector vector = new ASN1EncodableVector();
                ASN1Encodable rootOfTrust = null;

                for (ASN1Encodable asn1Encodable : teeEnforced) {
                    ASN1TaggedObject taggedObject = (ASN1TaggedObject) asn1Encodable;
                    if (taggedObject.getTagNo() == 704) continue;
                    vector.add(taggedObject);
                }
    
                LinkedList<Certificate> certificates;
                X509v3CertificateBuilder builder;
                ContentSigner signer;

                var k = keyboxes.get(leaf.getPublicKey().getAlgorithm());
                if (k == null)
                    throw new UnsupportedOperationException(
                            "unsupported algorithm " + leaf.getPublicKey().getAlgorithm());
                certificates = new LinkedList<>(k.certificates);
                builder = new X509v3CertificateBuilder(
                        new X509CertificateHolder(
                                certificates.get(0).getEncoded()
                        ).getSubject(),
                        leafHolder.getSerialNumber(),
                        leafHolder.getNotBefore(),
                        leafHolder.getNotAfter(),
                        leafHolder.getSubject(),
                        leafHolder.getSubjectPublicKeyInfo()
                );
                signer = new JcaContentSignerBuilder(leaf.getSigAlgName())
                        .build(k.keyPair.getPrivate());

                byte[] verifiedBootKey = new byte[32];
                byte[] verifiedBootHash = new byte[32];

                ThreadLocalRandom.current().nextBytes(verifiedBootKey);
                ThreadLocalRandom.current().nextBytes(verifiedBootHash);

                ASN1Encodable[] rootOfTrustEnc = {
                        new DEROctetString(verifiedBootKey), 
                        ASN1Boolean.TRUE, 
                        new ASN1Enumerated(0), 
                        new DEROctetString(verifiedBootHash)
                };
    
                ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEnc);
                ASN1TaggedObject rootOfTrustTagObj = new DERTaggedObject(704, rootOfTrustSeq);
                vector.add(rootOfTrustTagObj);
    
                ASN1Sequence hackEnforced = new DERSequence(vector);
                encodables[7] = hackEnforced;
                ASN1Sequence hackedSeq = new DERSequence(encodables);
    
                ASN1OctetString hackedSeqOctets = new DEROctetString(hackedSeq);
                Extension hackedExt = new Extension(OID, false, hackedSeqOctets);
                builder.addExtension(hackedExt);
    
                for (ASN1ObjectIdentifier extensionOID : leafHolder.getExtensions().getExtensionOIDs()) {
                    if (OID.getId().equals(extensionOID.getId())) continue;
                    builder.addExtension(leafHolder.getExtension(extensionOID));
                }
    
                certificates.addFirst(new JcaX509CertificateConverter().getCertificate(
                        builder.build(signer)));
    
                return certificates.toArray(new Certificate[0]);
            } catch (Throwable t) {
                Log.e(TAG, t.toString());
            }
            return caList;
        } else {
            if (USE_RATROAD && (isCallerSafetyNet() || sIsFinsky)) {
                dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
                throw new UnsupportedOperationException();
            }
            return caList;
        }
    }

    private static void dlog(String message) {
        if (DEBUG) Log.d(TAG, message);
    }

    public static class KeyBox {
        private final PEMKeyPair pemKeyPair;
        private final KeyPair keyPair;
        private final List<Certificate> certificates;

        public KeyBox(PEMKeyPair pemKeyPair, KeyPair keyPair, List<Certificate> certificates) {
            this.pemKeyPair = pemKeyPair;
            this.keyPair = keyPair;
            this.certificates = certificates;
        }
    }
}
