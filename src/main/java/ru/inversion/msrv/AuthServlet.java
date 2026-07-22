package ru.inversion.msrv;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.msrv.crypto.PasswordTools;
import ru.inversion.msrv.metrics.Metrics;
import ru.inversion.msrv.validation.Errors;
import ru.inversion.msrv.validation.XXIConnectException;
import ru.inversion.utils.AutoCloseableList;
import ru.inversion.utils.S;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;
import ru.inversion.utils.security.PasswordAuthenticationCleaner;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.PasswordAuthentication;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static org.slf4j.LoggerFactory.getLogger;

public class AuthServlet extends HttpServlet {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        final long t0 = System.nanoTime();

        String alias = null;
        int msAcquire = 0;
        int msDb = 0;
        int msEnc = 0;

        final Metrics.Scope servletSpan = Metrics.Tools.openSpan( req, "servlet", "AuthServlet", "handler_time" );

        final Metrics.Context mc = servletSpan.context();

        try( AutoCloseableList cleaners = new AutoCloseableList(true) )
        {
            final PasswordAuthentication auth = requireAttr( req, InputFilter.AUTH_CREDENTIAL_ATTR, PasswordAuthentication.class );
            final TargetContext           ctx = requireAttr( req, InputFilter.TARGET_CTX_ATTR,      TargetContext.class );

            cleaners.add( new PasswordAuthenticationCleaner(auth) );

            alias = ctx.alias();
            mc.put( Metrics.Key.AUTH_ALIAS, alias );

            final PasswordAuthentication toUse;

            final long tAcquire0 = System.nanoTime();

            try( Connection connection = ctx.dataSource().getConnection() ) {

                msAcquire = elapsedMs(tAcquire0);

                final long tDb0 = System.nanoTime();

                try {
                    toUse = ctx.vendorDb().serverSideLogin().apply( connection, auth, new Properties() );
                } catch( XXIConnectorException ex) {
                    throw Errors.fromConnector(ex);
                }

                cleaners.add( new PasswordAuthenticationCleaner(toUse) );

                msDb = elapsedMs(tDb0);

            } catch( SQLException ex ) {
                throw Errors.unavailable( ex, "Database call failed" );
            }

            final long tEnc0 = System.nanoTime();
            final PasswordTools.PasswordContainer pc = PasswordTools.encrypt ( auth.getPassword(), toUse.getUserName(), toUse.getPassword() );

            msEnc = elapsedMs(tEnc0);

            final IDco dco = new Dco("authResponse");
            dco.a("v").set("1");
            dco.e("status").set("OK");
            dco.e("login" ).set(toUse.getUserName());
            dco.e("alias" ).set( ctx.alias()   );
            dco.e("url"   ).set( ctx.jdbcUrl() );
            dco.e("vendor").set( ctx.vendorDb());

            final IDco xc = dco.e("passwordContainer");
            xc.e("nonce").cdata(pc.nonceBase64());
            xc.e("ciphertext").cdata(pc.ciphertextBase64());

            mc.put( Metrics.Key.AUTH_MS_ACQUIRE, msAcquire );
            mc.put( Metrics.Key.AUTH_MS_DB,      msDb  );
            mc.put( Metrics.Key.AUTH_MS_ENCRYPT, msEnc );

            mc.put(Metrics.Key.RESULT_OUTCOME,   "OK" );
            mc.put(Metrics.Key.RESP_HTTP_STATUS, HttpServletResponse.SC_OK );

            resp.setStatus( HttpServletResponse.SC_OK );
            resp.setContentType("application/xml; charset=utf-8");

            final String xmlOut = dco.asXml();

            resp.getWriter().write(xmlOut);

            final int msTotal = elapsedMs(t0);

            logger.info (
                "auth.ok alias={} http=200 ms_total={} ms_acquire={} ms_db={} ms_encrypt={}",
                alias, msTotal, msAcquire, msDb, msEnc
            );

        } catch( IOException | RuntimeException ex ) {

            servletSpan.fail(ex);

            mc.put( Metrics.Key.AUTH_MS_ACQUIRE, msAcquire );
            mc.put( Metrics.Key.AUTH_MS_DB,      msDb      );
            mc.put( Metrics.Key.AUTH_MS_ENCRYPT, msEnc     );

            throw ex;

        } finally {
            servletSpan.close();
        }
    }

    /** */
    private static int elapsedMs(long startedAtNano) {
        return (int) ( (System.nanoTime() - startedAtNano) / 1_000_000L );
    }

    /** */
    private static <T> T requireAttr( HttpServletRequest req, String name, Class<T> type ) {

        final Object value = req.getAttribute(name);

        if( value == null )
            throw Errors.of( Errors.ErrorCode.CONFIG_RUNTIME_INVALID, "Internal request attribute is missing: " + name );

        try {
            return TypeConverter.convert( value, type );
        } catch (Exception ex) {
            throw Errors.of( Errors.ErrorCode.CONFIG_RUNTIME_INVALID, "Internal request attribute has invalid type: " + name, ex );
        }
    }
}