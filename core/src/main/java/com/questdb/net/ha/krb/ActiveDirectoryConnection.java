/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2018 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.net.ha.krb;

import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import org.ietf.jgss.*;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

public class ActiveDirectoryConnection implements Closeable {

    private final static Log LOG = LogFactory.getLog(ActiveDirectoryConnection.class);
    private static final String JAAS_CONF = "/jaas.conf";
    private static final byte[] BYTES = new byte[0];
    private final Oid krb5MechOid;
    private final LoginContext loginContext;
    private final Subject subject;
    private final ServiceTicketEncoder encoder = new ServiceTicketEncoder();
    private final ServiceTicketDecoder decoder = new ServiceTicketDecoder();

    public ActiveDirectoryConnection(String krb5Conf, String principal, String keytab) throws IOException {
        URL cfg = this.getClass().getResource(JAAS_CONF);
        if (cfg == null) {
            throw new IOException("Not found: " + JAAS_CONF);
        }

        System.setProperty("java.security.auth.login.config", cfg.toString());
        System.setProperty("java.security.krb5.conf", krb5Conf);
        System.setProperty("questkrb.principal", principal);
        System.setProperty("questkrb.keytab", keytab);
        System.setProperty("sun.security.krb5.debug", LOG.isDebugEnabled() ? "true" : "false");
        try {
            loginContext = new LoginContext("Krb5KeyTabLoginContext");
            loginContext.login();
            subject = loginContext.getSubject();
        } catch (LoginException e) {
            throw new IOException("ActiveDirectory login failed. ", e);
        }

        try {
            krb5MechOid = new Oid("1.2.840.113554.1.2.2");
        } catch (GSSException e) {
            throw new IOException("Cannot create Kerberos Oid.", e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            loginContext.logout();
        } catch (LoginException e) {
            throw new IOException(e);
        }
    }

    public String decodeServiceToken(String serviceName, byte[] serviceToken) throws IOException {
        try {
            return Subject.doAs(subject, decoder.$(serviceName, serviceToken));
        } catch (PrivilegedActionException e) {
            throw new IOException(e);
        }
    }

    public byte[] encodeServiceToken(String serviceName) throws IOException {
        try {
            return Subject.doAs(subject, encoder.$(serviceName));
        } catch (PrivilegedActionException e) {
            throw new IOException(e);
        }
    }

    private class ServiceTicketEncoder implements PrivilegedExceptionAction<byte[]> {

        private String serviceName;

        public ServiceTicketEncoder $(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public byte[] run() throws Exception {
            GSSManager gssManager = GSSManager.getInstance();
            GSSName spn = gssManager.createName(serviceName, null);
            GSSCredential credential = gssManager.createCredential(null, GSSCredential.INDEFINITE_LIFETIME, krb5MechOid, GSSCredential.INITIATE_ONLY);
            GSSContext context = gssManager.createContext(spn, krb5MechOid, credential, GSSContext.DEFAULT_LIFETIME);
            try {
                return context.initSecContext(BYTES, 0, 0);
            } finally {
                context.dispose();
            }
        }
    }

    private class ServiceTicketDecoder implements PrivilegedExceptionAction<String> {

        private String serviceName;
        private byte[] serviceTicket;

        public ServiceTicketDecoder $(String serviceName, byte[] serviceTicket) {
            this.serviceName = serviceName;
            this.serviceTicket = serviceTicket;
            return this;
        }

        @Override
        public String run() throws Exception {
            GSSManager gssManager = GSSManager.getInstance();
            GSSName spn = gssManager.createName(serviceName, null);
            GSSCredential credential = gssManager.createCredential(null, GSSCredential.INDEFINITE_LIFETIME, krb5MechOid, GSSCredential.ACCEPT_ONLY);
            GSSContext context = gssManager.createContext(spn, krb5MechOid, credential, GSSContext.DEFAULT_LIFETIME);
            try {
                context.acceptSecContext(serviceTicket, 0, serviceTicket.length);
                return context.getSrcName().toString();
            } finally {
                context.dispose();
            }
        }
    }
}
