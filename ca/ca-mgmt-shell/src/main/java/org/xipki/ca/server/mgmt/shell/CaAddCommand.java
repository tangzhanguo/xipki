/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 - 2015 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

package org.xipki.ca.server.mgmt.shell;

import java.security.cert.X509Certificate;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.xipki.ca.server.mgmt.api.X509CAEntry;
import org.xipki.common.util.SecurityUtil;
import org.xipki.security.api.ConcurrentContentSigner;

/**
 * @author Lijun Liao
 */

@Command(scope = "xipki-ca", name = "ca-add", description="add CA")
public class CaAddCommand extends CaAddOrGenCommand
{
    @Option(name = "--cert",
            description = "CA certificate file")
    private String certFile;

    @Override
    protected Object _doExecute()
    throws Exception
    {
        X509CAEntry caEntry = getCAEntry();
        X509Certificate caCert = null;
        if(certFile != null)
        {
            caCert = SecurityUtil.parseCert(certFile);
        }

        // check whether the signer and certificate match
        ConcurrentContentSigner signer = securityFactory.createSigner(
                caEntry.getSignerType(), caEntry.getSignerConf(), caCert);

        // retrieve the certificate from the key token if not specified explicitly
        if(caCert == null)
        {
            caCert = signer.getCertificate();
        }

        caEntry.setCertificate(caCert);

        boolean b = caManager.addCA(caEntry);
        output(b, "added", "could not add", "CA " + caEntry.getName());
        return null;
    }
}
