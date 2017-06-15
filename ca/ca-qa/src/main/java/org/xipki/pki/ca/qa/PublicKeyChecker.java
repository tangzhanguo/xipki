/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ca.qa;

import java.security.spec.InvalidKeySpecException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.math.ec.ECCurve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.LruCache;
import org.xipki.common.qa.ValidationIssue;
import org.xipki.common.util.CollectionUtil;
import org.xipki.common.util.ParamUtil;
import org.xipki.pki.ca.api.BadCertTemplateException;
import org.xipki.pki.ca.api.profile.CertprofileException;
import org.xipki.pki.ca.api.profile.KeyParametersOption;
import org.xipki.pki.ca.api.profile.KeyParametersOption.AllowAllParametersOption;
import org.xipki.pki.ca.api.profile.KeyParametersOption.DSAParametersOption;
import org.xipki.pki.ca.api.profile.KeyParametersOption.ECParamatersOption;
import org.xipki.pki.ca.api.profile.KeyParametersOption.RSAParametersOption;
import org.xipki.security.util.AlgorithmUtil;
import org.xipki.security.util.X509Util;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class PublicKeyChecker {

    private static final Logger LOG = LoggerFactory.getLogger(PublicKeyChecker.class);

    private static final LruCache<ASN1ObjectIdentifier, Integer> EC_CURVEFIELD_SIZES
            = new LruCache<>(100);

    private Map<ASN1ObjectIdentifier, KeyParametersOption> keyAlgorithms;

    public PublicKeyChecker(final Map<ASN1ObjectIdentifier, KeyParametersOption> keyAlgorithms)
            throws CertprofileException {
        this.keyAlgorithms = keyAlgorithms;
    }

    public List<ValidationIssue> checkPublicKey(final SubjectPublicKeyInfo publicKey,
            final SubjectPublicKeyInfo requestedPublicKey) {
        ParamUtil.requireNonNull("publicKey", publicKey);
        ParamUtil.requireNonNull("requestedPublicKey", requestedPublicKey);

        List<ValidationIssue> resultIssues = new LinkedList<>();
        if (keyAlgorithms != null) {
            ValidationIssue issue = new ValidationIssue("X509.PUBKEY.SYN",
                    "whether the public key in certificate is permitted");
            resultIssues.add(issue);
            try {
                checkPublicKey(publicKey);
            } catch (BadCertTemplateException ex) {
                issue.setFailureMessage(ex.getMessage());
            }
        }

        ValidationIssue issue = new ValidationIssue("X509.PUBKEY.REQ",
                "whether public key matches the request one");
        resultIssues.add(issue);
        SubjectPublicKeyInfo c14nRequestedPublicKey;
        try {
            c14nRequestedPublicKey = X509Util.toRfc3279Style(requestedPublicKey);
            if (!c14nRequestedPublicKey.equals(publicKey)) {
                issue.setFailureMessage(
                        "public key in the certificate does not equal the requested one");
            }
        } catch (InvalidKeySpecException ex) {
            issue.setFailureMessage("public key in request is invalid");
        }

        return resultIssues;
    } // method checkPublicKey

    private void checkPublicKey(final SubjectPublicKeyInfo publicKey)
            throws BadCertTemplateException {
        if (CollectionUtil.isEmpty(keyAlgorithms)) {
            return;
        }

        ASN1ObjectIdentifier keyType = publicKey.getAlgorithm().getAlgorithm();
        if (!keyAlgorithms.containsKey(keyType)) {
            throw new BadCertTemplateException("key type " + keyType.getId() + " is not permitted");
        }

        KeyParametersOption keyParamsOption = keyAlgorithms.get(keyType);
        if (keyParamsOption instanceof AllowAllParametersOption) {
            return;
        } else if (keyParamsOption instanceof ECParamatersOption) {
            ECParamatersOption ecOption = (ECParamatersOption) keyParamsOption;
            // parameters
            ASN1Encodable algParam = publicKey.getAlgorithm().getParameters();
            ASN1ObjectIdentifier curveOid;

            if (algParam instanceof ASN1ObjectIdentifier) {
                curveOid = (ASN1ObjectIdentifier) algParam;
                if (!ecOption.allowsCurve(curveOid)) {
                    throw new BadCertTemplateException("EC curve "
                            + AlgorithmUtil.getCurveName(curveOid)
                            + " (OID: " + curveOid.getId() + ") is not allowed");
                }
            } else {
                throw new BadCertTemplateException(
                        "only namedCurve EC public key is supported");
            }

            // point encoding
            if (ecOption.getPointEncodings() != null) {
                byte[] keyData = publicKey.getPublicKeyData().getBytes();
                if (keyData.length < 1) {
                    throw new BadCertTemplateException("invalid publicKeyData");
                }
                byte pointEncoding = keyData[0];
                if (!ecOption.getPointEncodings().contains(pointEncoding)) {
                    throw new BadCertTemplateException(
                            "not-accepted EC point encoding " + pointEncoding);
                }
            }

            try {
                checkECSubjectPublicKeyInfo(curveOid, publicKey.getPublicKeyData().getBytes());
            } catch (BadCertTemplateException ex) {
                throw ex;
            } catch (Exception ex) {
                LOG.debug("checkECSubjectPublicKeyInfo", ex);
                throw new BadCertTemplateException("invalid public key: " + ex.getMessage());
            }

            return;
        } else if (keyParamsOption instanceof RSAParametersOption) {
            RSAParametersOption rsaOption = (RSAParametersOption) keyParamsOption;

            ASN1Integer modulus;
            try {
                ASN1Sequence seq = ASN1Sequence.getInstance(
                        publicKey.getPublicKeyData().getBytes());
                modulus = ASN1Integer.getInstance(seq.getObjectAt(0));
            } catch (IllegalArgumentException ex) {
                throw new BadCertTemplateException("invalid publicKeyData");
            }

            int modulusLength = modulus.getPositiveValue().bitLength();
            if ((rsaOption.allowsModulusLength(modulusLength))) {
                return;
            }
        } else if (keyParamsOption instanceof DSAParametersOption) {
            DSAParametersOption dsaOption = (DSAParametersOption) keyParamsOption;
            ASN1Encodable params = publicKey.getAlgorithm().getParameters();
            if (params == null) {
                throw new BadCertTemplateException("null Dss-Parms is not permitted");
            }

            int plength;
            int qlength;

            try {
                ASN1Sequence seq = ASN1Sequence.getInstance(params);
                // CHECKSTYLE:SKIP
                ASN1Integer p = ASN1Integer.getInstance(seq.getObjectAt(0));
                // CHECKSTYLE:SKIP
                ASN1Integer q = ASN1Integer.getInstance(seq.getObjectAt(1));
                plength = p.getPositiveValue().bitLength();
                qlength = q.getPositiveValue().bitLength();
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ex) {
                throw new BadCertTemplateException("illegal Dss-Parms");
            }

            boolean match = dsaOption.allowsPlength(plength);
            if (match) {
                match = dsaOption.allowsQlength(qlength);
            }

            if (match) {
                return;
            }
        } else {
            String txt = (keyParamsOption == null) ? "null" : keyParamsOption.getClass().getName();
            throw new RuntimeException("should not reach here, unknown keyParamsOption " + txt);
        }

        throw new BadCertTemplateException("the given publicKey is not permitted");
    } // method checkPublicKey

    // CHECKSTYLE:SKIP
    private static void checkECSubjectPublicKeyInfo(final ASN1ObjectIdentifier curveOid,
            final byte[] encoded)
            throws BadCertTemplateException {
        Integer expectedLength = EC_CURVEFIELD_SIZES.get(curveOid);
        if (expectedLength == null) {
            X9ECParameters ecP = ECUtil.getNamedCurveByOid(curveOid);
            ECCurve curve = ecP.getCurve();
            expectedLength = (curve.getFieldSize() + 7) / 8;
            EC_CURVEFIELD_SIZES.put(curveOid, expectedLength);
        }

        switch (encoded[0]) {
        case 0x02: // compressed
        case 0x03: // compressed
            if (encoded.length != (expectedLength + 1)) {
                throw new BadCertTemplateException("incorrect length for compressed encoding");
            }
            break;
        case 0x04: // uncompressed
        case 0x06: // hybrid
        case 0x07: // hybrid
            if (encoded.length != (2 * expectedLength + 1)) {
                throw new BadCertTemplateException(
                        "incorrect length for uncompressed/hybrid encoding");
            }
            break;
        default:
            throw new BadCertTemplateException(
                    "invalid point encoding 0x" + Integer.toString(encoded[0], 16));
        } // end switch
    } // method checkECSubjectPublicKeyInfo

}
