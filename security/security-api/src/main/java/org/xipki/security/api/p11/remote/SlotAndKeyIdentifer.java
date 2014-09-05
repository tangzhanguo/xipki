/*
 * Copyright (c) 2014 Lijun Liao
 *
 * TO-BE-DEFINE
 *
 */

package org.xipki.security.api.p11.remote;

import java.io.IOException;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;

/**
 *
 * <pre>
 * SlotAndKeyIdentifer ::= SEQUENCE
 * {
 *     slotIdentifier   SlotIdentifier,
 *     keyIdentifier    KeyIdentifier,
 * }
 * </pre>
 *
 * @author Lijun Liao
 */

public class SlotAndKeyIdentifer extends ASN1Object
{
    private SlotIdentifier slotIdentifier;
    private KeyIdentifier keyIdentifier;

    public SlotAndKeyIdentifer(SlotIdentifier slotIdentifier,
            KeyIdentifier keyIdentifier)
    {
        if(slotIdentifier == null)
        {
            throw new IllegalArgumentException("slotIdentifier could not be null");
        }

        if(keyIdentifier == null)
        {
            throw new IllegalArgumentException("keyIdentifier could not be null");
        }

        this.slotIdentifier = slotIdentifier;
        this.keyIdentifier = keyIdentifier;
    }

    private SlotAndKeyIdentifer(ASN1Sequence seq)
    {
        if (seq.size() != 2)
        {
            throw new IllegalArgumentException("wrong number of elements in sequence");
        }

        this.slotIdentifier = SlotIdentifier.getInstance(seq.getObjectAt(0));
        this.keyIdentifier = KeyIdentifier.getInstance(seq.getObjectAt(1));
    }

    public static SlotAndKeyIdentifer getInstance(
            Object obj)
    {
        if (obj == null || obj instanceof SlotAndKeyIdentifer)
        {
            return (SlotAndKeyIdentifer)obj;
        }

        if (obj instanceof ASN1Sequence)
        {
            return new SlotAndKeyIdentifer((ASN1Sequence) obj);
        }

        if (obj instanceof byte[])
        {
            try
            {
                return getInstance(ASN1Primitive.fromByteArray((byte[])obj));
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("unable to parse encoded general name");
            }
        }

        throw new IllegalArgumentException("unknown object in getInstance: " + obj.getClass().getName());
    }

    @Override
    public ASN1Primitive toASN1Primitive()
    {
        ASN1EncodableVector vector = new ASN1EncodableVector();
        vector.add(slotIdentifier.toASN1Primitive());
        vector.add(keyIdentifier.toASN1Primitive());
        return new DERSequence(vector);
    }

    public SlotIdentifier getSlotIdentifier()
    {
        return slotIdentifier;
    }

    public KeyIdentifier getKeyIdentifier()
    {
        return keyIdentifier;
    }

}