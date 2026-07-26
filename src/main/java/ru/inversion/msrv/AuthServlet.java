package ru.inversion.msrv;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.msrv.crypto.PasswordTools;
import ru.inversion.msrv.validation.Errors;
import ru.inversion.utils.AutoCloseableList;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;
import ru.inversion.utils.security.PasswordAuthenticationCleaner;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.PasswordAuthentication;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

import static org.slf4j.LoggerFactory.getLogger;

public class AuthServlet extends HttpServlet {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    private final MeterRegistry meterRegistry;

    /** */
    public AuthServlet(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {


        String alias = null;

        try( AutoCloseableList cleaners = new AutoCloseableList(true) )
        {
            final PasswordAuthentication auth = requireAttr( req, InputFilter.AUTH_CREDENTIAL_ATTR, PasswordAuthentication.class );
            final TargetContext           ctx = requireAttr( req, InputFilter.TARGET_CTX_ATTR,      TargetContext.class );

            cleaners.add( new PasswordAuthenticationCleaner(auth) );

            alias = ctx.alias();

            final PasswordAuthentication toUse;

            final Timer.Sample acquireSample = Timer.start(meterRegistry);
            final Connection connection;

            try {

                connection = ctx.dataSource().getConnection();


                try {
                    toUse = ctx.vendorDb().serverSideLogin().apply( connection, auth, new Properties() );
                } catch( XXIConnectorException ex) {
                    throw Errors.fromConnector(ex);
                }

                cleaners.add( new PasswordAuthenticationCleaner(toUse) );

                stopTimer(
                        acquireSample,
                        "xxi.auth.acquire",
                        ctx,
                        "success"
                );
            } catch( SQLException ex ) {

                stopTimer(
                        acquireSample,
                        "xxi.auth.acquire",
                        ctx,
                        "failure"
                );

                throw Errors.unavailable( ex, "Database call failed" );
            }

            final Timer.Sample encryptSample = Timer.start(meterRegistry);
            final PasswordTools.PasswordContainer pc;

            try {
                pc = PasswordTools.encrypt( auth.getPassword(), toUse.getUserName(), toUse.getPassword() );

                stopTimer(
                        encryptSample,
                        "xxi.auth.encrypt",
                        ctx,
                        "success"
                );

            } catch (RuntimeException ex) {

                stopTimer(
                        encryptSample,
                        "xxi.auth.encrypt",
                        ctx,
                        "failure"
                );

                throw ex;
            }

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

            resp.setStatus( HttpServletResponse.SC_OK );
            resp.setContentType("application/xml; charset=utf-8");

            final String xmlOut = dco.asXml();

            resp.getWriter().write(xmlOut);

            logger.atInfo()
                    .addKeyValue("alias", ctx.alias())
                    .addKeyValue("vendor", ctx.vendorDb().name())
                    .addKeyValue("http_status", 200)
                    .log("auth.ok");

        } catch( IOException | RuntimeException ex ) {

            throw ex;

        }
    }

    private void stopTimer(
            Timer.Sample sample,
            String metricName,
            TargetContext ctx,
            String outcome
    ) {
        sample.stop(
                meterRegistry.timer(
                        metricName,
                        "target", ctx.alias(),
                        "vendor", ctx.vendorDb().name(),
                        "outcome", outcome
                )
        );
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